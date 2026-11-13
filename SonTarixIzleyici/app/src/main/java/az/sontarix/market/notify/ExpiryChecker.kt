package az.sontarix.market.notify

import android.content.Context
import az.sontarix.market.appContainer
import az.sontarix.market.data.AppSettings
import az.sontarix.market.data.NotificationLog
import az.sontarix.market.data.Product
import az.sontarix.market.data.Stats
import az.sontarix.market.util.DateUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime

object ExpiryChecker {
    private val mutex = Mutex()
    private const val MAX_INDIVIDUAL = 20

    fun computeStats(products: List<Product>, warningDays: Int, today: LocalDate = DateUtils.today()): Stats {
        var normal = 0; var approaching = 0; var week = 0; var todayCount = 0; var expired = 0; var qty = 0L
        products.forEach { p ->
            val d = p.daysLeft(today)
            qty += p.quantity
            when {
                d < 0 -> expired++
                d == 0L -> todayCount++
                d <= warningDays -> { approaching++; if (d <= 7) week++ }
                else -> normal++
            }
        }
        return Stats(products.size, normal, approaching, week, todayCount, expired, qty)
    }

    /**
     * Ən təcili keçilmiş həddi tapır. Məs: hədlər 30,15,7,3,1,0 və 5 gün qalıbsa → 7.
     * Müddəti keçibsə → -1 (əgər aktivdirsə).
     */
    fun thresholdFor(daysLeft: Long, settings: AppSettings): Int? = when {
        daysLeft < 0 -> if (settings.notifyExpired) -1 else null
        else -> settings.thresholds.filter { daysLeft <= it }.minOrNull()
    }

    /**
     * Bütün məhsulları yoxlayır və lazım olan bildirişləri göndərir.
     * Hər (məhsul, tarix, hədd) üçün yalnız bir dəfə bildiriş göndərilir.
     * @param scheduledRun planlaşdırılmış gündəlik işə salınma (gündəlik hesabat üçün)
     */
    suspend fun run(context: Context, scheduledRun: Boolean) = mutex.withLock {
        val c = context.appContainer
        val settings = c.settings.current
        val dao = c.db.productDao()
        val logDao = c.db.notificationLogDao()
        val today = DateUtils.today()
        val products = dao.getAll()

        if (!settings.notificationsEnabled) return@withLock

        var shown = 0
        var overflow = 0
        for (p in products) {
            val days = p.daysLeft(today)
            val threshold = thresholdFor(days, settings) ?: continue
            if (logDao.exists(p.id, p.expiryEpochDay, threshold) > 0) continue
            val content = NotificationHelper.buildContent(p, days)
            val canShow = shown < MAX_INDIVIDUAL
            if (canShow) {
                NotificationHelper.showExpiry(context, p, days, settings.soundEnabled, content)
                shown++
            } else {
                overflow++
            }
            logDao.insert(
                NotificationLog(
                    productId = p.id, expiryEpochDay = p.expiryEpochDay, threshold = threshold,
                    title = content.title, message = content.body, shown = true
                )
            )
            // Daha az təcili hədləri də "keçilmiş" kimi qeyd et ki, sonradan köhnə bildiriş gəlməsin
            settings.thresholds.filter { it > threshold }.forEach { t ->
                logDao.insert(NotificationLog(productId = p.id, expiryEpochDay = p.expiryEpochDay, threshold = t, shown = false))
            }
        }
        if (shown > 1) NotificationHelper.showGroupSummary(context, shown + overflow, settings.soundEnabled)
        if (overflow > 0) NotificationHelper.showOverflow(context, overflow, settings.soundEnabled)

        if (settings.dailySummary && c.settings.lastSummaryEpochDay != today.toEpochDay()) {
            val due = LocalTime.now() >= LocalTime.of(settings.notifyHour, settings.notifyMinute)
            if (scheduledRun || due) {
                NotificationHelper.showDailySummary(context, computeStats(products, settings.warningDays, today), settings.soundEnabled)
                c.settings.lastSummaryEpochDay = today.toEpochDay()
            }
        }
    }
}
