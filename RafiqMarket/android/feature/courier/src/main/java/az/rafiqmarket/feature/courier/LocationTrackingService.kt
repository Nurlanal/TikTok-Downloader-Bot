package az.rafiqmarket.feature.courier

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import az.rafiqmarket.core.network.AppException
import az.rafiqmarket.core.network.RafiqApi
import az.rafiqmarket.core.network.apiCall
import az.rafiqmarket.core.network.dto.LocationBody
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Kuryerin canlı GPS mövqeyini serverə ötürən foreground service.
 * - Yalnız kuryer sifarişi götürüb çatdırılmaya başladıqda işə düşür.
 * - Daimi bildiriş göstərir (Android tələbi).
 * - Server "aktiv çatdırılma yoxdur" cavabı verdikdə (sifariş tamamlandı/ləğv edildi) özünü dayandırır.
 */
@AndroidEntryPoint
class LocationTrackingService : LifecycleService() {

    @Inject lateinit var api: RafiqApi

    private lateinit var fused: FusedLocationProviderClient
    private var started = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            lifecycleScope.launch {
                apiCall {
                    api.sendLocation(
                        LocationBody(
                            lat = loc.latitude, lng = loc.longitude,
                            accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
                            heading = if (loc.hasBearing()) loc.bearing.toDouble() else null,
                            speed = if (loc.hasSpeed()) loc.speed.toDouble() else null,
                        )
                    )
                }.onSuccess {
                    _lastSentAt.value = System.currentTimeMillis()
                }.onFailure { e ->
                    if ((e as? AppException)?.code == "NO_ACTIVE_DELIVERY" || (e as? AppException)?.httpStatus == 403) stopTracking()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) { stopTracking(); return START_NOT_STICKY }
        if (!hasPermission(this)) { stopSelf(); return START_NOT_STICKY }
        if (!started) start()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        started = true
        createChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(3_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        _running.value = true
    }

    private fun stopTracking() {
        if (::fused.isInitialized) fused.removeLocationUpdates(callback)
        started = false
        _running.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (::fused.isInitialized) fused.removeLocationUpdates(callback)
        _running.value = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pi = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Rafiq Market – çatdırılma")
            .setContentText("Mövqeyiniz yalnız sifarişin müştərisi ilə paylaşılır")
            .setOngoing(true)
            .setContentIntent(pi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "courier_tracking"
        private const val NOTIF_ID = 4201
        private const val ACTION_STOP = "az.rafiqmarket.STOP_TRACKING"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
        private val _lastSentAt = MutableStateFlow<Long?>(null)
        val lastSentAt: StateFlow<Long?> = _lastSentAt

        fun hasPermission(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun start(ctx: Context) {
            if (!hasPermission(ctx)) return
            ContextCompat.startForegroundService(ctx, Intent(ctx, LocationTrackingService::class.java))
        }

        fun stop(ctx: Context) {
            if (!_running.value) return
            ctx.startService(Intent(ctx, LocationTrackingService::class.java).setAction(ACTION_STOP))
        }

        private fun createChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Canlı GPS paylaşımı", NotificationManager.IMPORTANCE_LOW))
        }
    }
}
