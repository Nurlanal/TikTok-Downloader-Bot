package az.payreport.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import az.payreport.app.PayReportApp
import java.util.concurrent.TimeUnit

/** Fon sinxronizasiyası (WorkManager). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PayReportApp).container
        val trigger = inputData.getString(KEY_TRIGGER) ?: "AUTO"
        val outcome = container.syncManager.syncNow(trigger)
        return if (outcome.ok || runAttemptCount >= 3) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_TRIGGER = "trigger"
        private const val PERIODIC = "payreport-periodic-sync"
        private const val ONE_TIME = "payreport-sync-now"

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Dövri sinxronizasiyanı planlaşdırır (minimum 15 dəqiqə — Android məhdudiyyəti). */
        fun schedule(context: Context, intervalMinutes: Int, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val req = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_TRIGGER to "AUTO"))
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_TRIGGER to "MANUAL"))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.KEEP, req)
        }
    }
}
