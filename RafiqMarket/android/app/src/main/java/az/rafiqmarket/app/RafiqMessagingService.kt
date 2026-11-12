package az.rafiqmarket.app

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import az.rafiqmarket.core.session.SessionStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Firebase Cloud Messaging: müştəri, kuryer və admin bildirişləri */
@AndroidEntryPoint
class RafiqMessagingService : FirebaseMessagingService() {
    @Inject lateinit var push: PushTokenSync
    @Inject lateinit var store: SessionStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { if (store.current() != null) push.send(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Rafiq Market"
        val body = message.notification?.body ?: message.data["body"] ?: return
        val orderId = message.data["orderId"]?.toLongOrNull()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { orderId?.let { putExtra(MainActivity.EXTRA_ORDER_ID, it) } }
        val pi = PendingIntent.getActivity(this, (orderId ?: 0L).toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, RafiqApp.ORDERS_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .build()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < 33) {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), n)
        }
    }
}
