package az.sontarix.market.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import az.sontarix.market.MainActivity
import az.sontarix.market.R
import az.sontarix.market.data.Product
import az.sontarix.market.data.Stats
import az.sontarix.market.util.DateUtils
import az.sontarix.market.util.ImageStorage

object NotificationHelper {
    const val CHANNEL_SOUND = "expiry_alerts_sound"
    const val CHANNEL_SILENT = "expiry_alerts_silent"
    const val CHANNEL_SUMMARY = "daily_summary"
    const val EXTRA_PRODUCT_ID = "product_id"
    const val EXTRA_OPEN = "open_screen"
    private const val GROUP = "az.sontarix.EXPIRY"
    private const val SUMMARY_ID = 1
    private const val GROUP_SUMMARY_ID = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val sound = NotificationChannel(CHANNEL_SOUND, "Son istifadə xəbərdarlıqları", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Məhsulların son istifadə tarixi yaxınlaşdıqda səsli bildiriş"
            enableVibration(true)
        }
        val silent = NotificationChannel(CHANNEL_SILENT, "Son istifadə xəbərdarlıqları (səssiz)", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Səssiz rejimdə göndərilən xəbərdarlıqlar"
            setSound(null, null)
            enableVibration(false)
        }
        val summary = NotificationChannel(CHANNEL_SUMMARY, "Gündəlik hesabat", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Hər gün məhsulların ümumi vəziyyəti"
        }
        nm.createNotificationChannels(listOf(sound, silent, summary))
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notificationId(productId: Long): Int = (1000 + (productId % 2_000_000_000L)).toInt()

    data class Content(val title: String, val body: String)

    fun buildContent(product: Product, daysLeft: Long): Content {
        val title = when {
            daysLeft < 0 -> "❌ MƏHSULUN SON İSTİFADƏ TARİXİ KEÇİB!"
            daysLeft == 0L -> "⛔ SON İSTİFADƏ TARİXİ BU GÜN BİTİR!"
            else -> "⚠️ SON İSTİFADƏ TARİXİ YAXINLAŞIR!"
        }
        val remaining = when {
            daysLeft < 0 -> "${-daysLeft} gün əvvəl bitib."
            daysLeft == 0L -> "Bu gün bitir."
            else -> "$daysLeft gün."
        }
        val body = buildString {
            append("Məhsul: ").append(product.name).append('\n')
            append("Miqdar: ").append(product.quantity).append(" ədəd\n")
            if (product.batchNumber.isNotBlank()) append("Partiya: ").append(product.batchNumber).append('\n')
            append("Son istifadə tarixi: ").append(DateUtils.format(product.expiryDate)).append('\n')
            append(if (daysLeft < 0) "Vəziyyət: " else "Qalan vaxt: ").append(remaining)
        }
        return Content(title, body)
    }

    private fun openProductIntent(context: Context, productId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_PRODUCT_ID, productId)
        }
        return PendingIntent.getActivity(
            context, notificationId(productId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openScreenIntent(context: Context, screen: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN, screen)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @Suppress("MissingPermission")
    fun showExpiry(context: Context, product: Product, daysLeft: Long, sound: Boolean, content: Content) {
        if (!canNotify(context)) return
        val builder = NotificationCompat.Builder(context, if (sound) CHANNEL_SOUND else CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText("${product.name} • ${DateUtils.remainingText(daysLeft)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setPriority(if (sound) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(colorFor(daysLeft))
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setContentIntent(openProductIntent(context, product.id))
        if (!sound) builder.setSilent(true)
        ImageStorage.loadThumbnail(context, product.imageFile, 192)?.let { builder.setLargeIcon(it) }
        NotificationManagerCompat.from(context).notify(notificationId(product.id), builder.build())
    }

    @Suppress("MissingPermission")
    fun showGroupSummary(context: Context, count: Int, sound: Boolean) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, if (sound) CHANNEL_SOUND else CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Son istifadə xəbərdarlıqları")
            .setContentText("$count məhsul diqqət tələb edir")
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(openScreenIntent(context, "notifications", 3))
            .build()
        NotificationManagerCompat.from(context).notify(GROUP_SUMMARY_ID, n)
    }

    @Suppress("MissingPermission")
    fun showOverflow(context: Context, extra: Int, sound: Boolean) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, if (sound) CHANNEL_SOUND else CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠️ Daha $extra məhsulun tarixi yaxınlaşır")
            .setContentText("Siyahını görmək üçün toxunun")
            .setGroup(GROUP)
            .setAutoCancel(true)
            .setContentIntent(openScreenIntent(context, "notifications", 4))
            .build()
        NotificationManagerCompat.from(context).notify(5, n)
    }

    @Suppress("MissingPermission")
    fun showDailySummary(context: Context, stats: Stats, sound: Boolean) {
        if (!canNotify(context)) return
        val body = buildString {
            append("Ümumi məhsul: ${stats.total}\n")
            append("Normal: ${stats.normal}\n")
            append("Tarixi yaxınlaşan: ${stats.approaching}\n")
            append("Bu gün bitən: ${stats.today}\n")
            append("Müddəti keçmiş: ${stats.expired}")
        }
        val n = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📊 Gündəlik hesabat — ${DateUtils.format(DateUtils.today())}")
            .setContentText("Yaxınlaşan: ${stats.approaching} • Bu gün: ${stats.today} • Keçmiş: ${stats.expired}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .apply { if (!sound) setSilent(true) }
            .setContentIntent(openScreenIntent(context, "dashboard", 6))
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_ID, n)
    }

    /** Parametrlərdən test bildirişi. */
    fun showTest(context: Context, sound: Boolean) {
        val sample = Product(
            id = 999_999L, uid = "test", name = "Süd 1L (test)", quantity = 20,
            expiryEpochDay = DateUtils.today().plusDays(3).toEpochDay()
        )
        showExpiry(context, sample, 3, sound, buildContent(sample, 3))
    }

    fun cancelForProduct(context: Context, productId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(productId))
    }

    private fun colorFor(daysLeft: Long): Int = when {
        daysLeft < 0 -> 0xFFDC2626.toInt()
        daysLeft <= 7 -> 0xFFF97316.toInt()
        else -> 0xFFEAB308.toInt()
    }
}
