package az.rafiqmarket.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RafiqApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ORDERS_CHANNEL, "Sifariş bildirişləri", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Sifariş statusu, yeni təyinatlar və market bildirişləri"
            }
        )
    }

    companion object { const val ORDERS_CHANNEL = "orders" }
}
