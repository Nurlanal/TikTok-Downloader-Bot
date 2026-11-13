package az.sontarix.market.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import az.sontarix.market.appContainer
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Planlaşdırılmış yoxlamanı icra edən işçi. Tətbiq bağlı olsa da işləyir. */
class ExpiryCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        ExpiryChecker.run(applicationContext, scheduledRun = inputData.getBoolean(KEY_SCHEDULED, false))
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object { const val KEY_SCHEDULED = "scheduled" }
}

/**
 * İki səviyyəli planlaşdırma:
 * 1) AlarmManager — seçilmiş saatda gündəlik siqnal (Doze rejimində də icazəli).
 * 2) WorkManager periodik iş — ehtiyat mexanizm (yenidən başladıqdan sonra avtomatik bərpa olunur).
 * Təkrar bildirişlərin qarşısını NotificationLog cədvəli alır.
 */
object NotificationScheduler {
    private const val PERIODIC = "expiry_periodic"
    private const val ONE_TIME = "expiry_now"
    private const val DAILY = "expiry_daily_alarm"

    fun scheduleAll(context: Context) {
        schedulePeriodic(context, replace = false)
        scheduleDailyAlarm(context)
    }

    /** Parametrlər dəyişdikdə hər şeyi yenidən planlaşdır. */
    fun reschedule(context: Context) {
        schedulePeriodic(context, replace = true)
        scheduleDailyAlarm(context)
        runCheckNow(context)
    }

    fun runCheckNow(context: Context, scheduled: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<ExpiryCheckWorker>()
            .setInputData(workDataOf(ExpiryCheckWorker.KEY_SCHEDULED to scheduled))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            if (scheduled) DAILY else ONE_TIME, ExistingWorkPolicy.REPLACE, req
        )
    }

    private fun delayToNextRun(context: Context): Long {
        val s = context.appContainer.settings.current
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(s.notifyHour, s.notifyMinute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    private fun nextRunMillis(context: Context): Long =
        System.currentTimeMillis() + delayToNextRun(context)

    private fun schedulePeriodic(context: Context, replace: Boolean) {
        val req = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(24, TimeUnit.HOURS, 2, TimeUnit.HOURS)
            .setInitialDelay(delayToNextRun(context), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ExpiryCheckWorker.KEY_SCHEDULED to true))
            .setConstraints(Constraints.NONE)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 100, Intent(context, DailyAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun scheduleDailyAlarm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        am.cancel(pi)
        // Dəqiq alarm icazəsi tələb etməyən, Doze rejimində də işləyən siqnal
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunMillis(context), pi)
    }
}

/** Gündəlik siqnal gəldikdə yoxlamanı işə salır və növbəti günü planlaşdırır. */
class DailyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationScheduler.runCheckNow(context, scheduled = true)
        NotificationScheduler.scheduleDailyAlarm(context)
    }
}

/** Telefon yenidən başladıqda / tətbiq yeniləndikdə / saat dəyişdikdə bildirişləri bərpa edir. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                NotificationHelper.createChannels(context)
                NotificationScheduler.scheduleAll(context)
                NotificationScheduler.runCheckNow(context)
            }
        }
    }
}
