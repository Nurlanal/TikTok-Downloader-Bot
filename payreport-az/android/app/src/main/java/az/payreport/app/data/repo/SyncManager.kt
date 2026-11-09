package az.payreport.app.data.repo

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
import androidx.room.withTransaction
import az.payreport.app.MainActivity
import az.payreport.app.R
import az.payreport.app.data.db.AppDatabase
import az.payreport.app.data.mail.DirectMailSync
import az.payreport.app.data.remote.ApiClient
import az.payreport.app.data.settings.AppSettings
import az.payreport.app.data.settings.SyncMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncOutcome(val ok: Boolean, val created: Int, val pending: Int, val message: String)

/** Seçilmiş rejimə görə sinxronizasiya: birbaşa IMAP və ya serverdən çəkmə. */
class SyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: AppSettings,
    private val direct: DirectMailSync,
    private val api: ApiClient,
) {
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    suspend fun syncNow(trigger: String): SyncOutcome = mutex.withLock {
        _running.value = true
        try {
            val outcome = when (settings.current.mode) {
                SyncMode.DIRECT -> {
                    val r = direct.sync(trigger)
                    // Çox məktub varsa, qalanını dərhal fon rejimində davam etdiririk
                    if (r.status == "SUCCESS" && direct.lastRemaining > 0) az.payreport.app.work.SyncWorker.continueSoon(context)
                    SyncOutcome(r.status == "SUCCESS", r.created, r.pending, r.message.orEmpty())
                }
                SyncMode.SERVER -> pullFromServer(trigger)
            }
            settings.setLastSync(System.currentTimeMillis(), if (outcome.ok) "OK" else "ERROR", outcome.message)
            if (outcome.ok && (outcome.created > 0 || outcome.pending > 0)) notify(outcome)
            outcome
        } finally {
            _running.value = false
        }
    }

    private suspend fun pullFromServer(trigger: String): SyncOutcome {
        if (!settings.current.serverLoggedIn) return SyncOutcome(false, 0, 0, "Serverə daxil olunmayıb (Ayarlar → Server)")
        return try {
            if (trigger == "MANUAL") runCatching { api.runServerSync() } // serverdən dərhal mail yoxlamasını istəyirik
            var created = 0
            var pending = 0
            var total = 0
            while (true) {
                val page = api.payments(settings.serverCursorUpdatedAt, settings.serverCursorId)
                if (page.items.isEmpty()) break
                val existing = page.items.mapNotNull { db.payments().getById(it.id)?.id }.toSet()
                db.withTransaction { db.payments().upsertAll(page.items.map { it.toEntity() }) }
                page.items.filter { it.id !in existing }.forEach { if (it.status == "PENDING") pending++ else if (it.status == "CONFIRMED") created++ }
                total += page.items.size
                val last = page.items.last()
                settings.serverCursorUpdatedAt = last.updatedAt
                settings.serverCursorId = last.id
                if (page.items.size < 500) break
            }
            runCatching { db.audit().upsertAll(api.audit(db.audit().maxId()).items.map { it.toEntity() }) }
            runCatching { db.syncRuns().upsertAll(api.syncRuns().items.map { it.toEntity() }) }
            SyncOutcome(true, created, pending, "Serverdən $total dəyişiklik alındı ($created yeni, $pending gözləyən)")
        } catch (e: Exception) {
            SyncOutcome(false, 0, 0, e.message ?: "Server xətası")
        }
    }

    /**
     * Maildən gələn ödənişləri silib məktubları son [days] gün üzrə yenidən oxuyur (birbaşa rejim).
     * Parser qaydaları yeniləndikdən sonra köhnə səhv hesabı düzəltmək üçündür.
     * Əl ilə daxil edilmiş ödənişlər saxlanılır; əməliyyat audit jurnalına yazılır.
     */
    suspend fun reprocessMail(days: Int): SyncOutcome {
        mutex.withLock {
            db.withTransaction {
                val removed = db.payments().deleteFromMail()
                db.processedEmails().deleteAll()
                db.audit().insert(
                    az.payreport.app.data.db.AuditEntity(
                        paymentId = null, action = "REPROCESS", actor = "admin (cihaz)",
                        details = "{\"removedMailPayments\":$removed,\"days\":$days}",
                    )
                )
            }
            settings.setMail(settings.current.mail.copy(initialDays = days))
            settings.resetImapCursor()
        }
        return syncNow("MANUAL")
    }

    /** Rejim dəyişəndə lokal keşi təmizləyir. */
    suspend fun clearLocalData() {
        db.withTransaction {
            db.payments().deleteAll()
            db.audit().deleteAll()
            db.syncRuns().deleteAll()
            db.processedEmails().deleteAll()
        }
        settings.resetImapCursor()
        settings.resetServerCursor()
    }

    private fun notify(o: SyncOutcome) {
        if (!settings.current.notifications) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Yeni ödənişlər", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = buildString {
            if (o.created > 0) append("${o.created} yeni ödəniş təsdiqləndi")
            if (o.pending > 0) { if (isNotEmpty()) append(", "); append("${o.pending} gözləyən") }
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("PayReport AZ")
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1001, n)
    }

    companion object {
        const val CHANNEL = "payments"
    }
}
