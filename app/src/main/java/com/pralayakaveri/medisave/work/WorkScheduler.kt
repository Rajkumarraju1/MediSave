package com.pralayakaveri.medisave.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pralayakaveri.medisave.worker.DailyResetWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkScheduler {

    fun scheduleDailyReset(context: Context) {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
        val delayMs = java.time.Duration.between(now, nextMidnight).toMillis()
        
        val targetTime = nextMidnight.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        android.util.Log.d("WorkScheduler", "Scheduling next DailyResetWork for $targetTime in zone $zone (Delay: ${delayMs/1000}s)")

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<DailyResetWorker>()
            .setInitialDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addTag("DailyResetWork")
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            "daily_reset_exact",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun scheduleRefillReminder(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<RefillReminderWorker>(
            8, TimeUnit.HOURS
        ).build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "RefillReminderWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun scheduleSyncWorker(context: Context) {
        val syncRequest = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "MEDISAVE_SYNC_WORK",
            androidx.work.ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }
}
