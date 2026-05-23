package com.pralayakaveri.medisave.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.model.Medicine
import com.pralayakaveri.medisave.work.BootRescheduleWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.BackoffPolicy
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received action: $action")

        if (action == android.content.Intent.ACTION_BOOT_COMPLETED || 
            action == android.content.Intent.ACTION_TIMEZONE_CHANGED || 
            action == "android.intent.action.TIME_SET" ||
            action == "com.pralayakaveri.medisave.TRIGGER_RESCHEDULE") {

            val pendingResult = goAsync()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            scope.launch {
                try {
                    // 1. Bounded Reboot Rescheduling (schedule top 5 imminent alarms immediately in the receiver)
                    performBoundedReschedule(context)

                    // 2. Defer the comprehensive scan and catch-up database writes to BootRescheduleWorker
                    enqueueBootRescheduleWorker(context)

                    // 3. Schedule daily reset task
                    com.pralayakaveri.medisave.work.WorkScheduler.scheduleDailyReset(context)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error processing boot completed inside goAsync scope", e)
                } finally {
                    pendingResult.finish()
                    scope.cancel()
                }
            }
        }
    }

    private suspend fun performBoundedReschedule(context: Context) {
        val db = AppDatabase.getDatabase(context)
        
        // Resolve userId offline-first through multi-tiered sources
        var userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (userId.isNullOrEmpty()) {
            val preferenceManager = com.pralayakaveri.medisave.data.PreferenceManager(context)
            userId = preferenceManager.sessionUserId.firstOrNull()
        }
        if (userId.isNullOrEmpty()) {
            userId = db.userDao().getPrimaryUser()?.userId
        }
        if (userId.isNullOrEmpty()) {
            userId = "primary" // Safe fallback
        }

        val allReminders = db.medicineReminderDao().getAllReminders()
        val reminderManager = ReminderManager(context)

        Log.d("BootReceiver", "Reboot recovery: Resolving userId: $userId. Found ${allReminders.size} medicines.")

        val now = System.currentTimeMillis()
        val twelveHoursLater = now + 12 * 60 * 60 * 1000L

        data class ImminentAlarm(
            val medicine: Medicine,
            val time: String,
            val triggerTimeMs: Long
        )

        val imminentAlarms = mutableListOf<ImminentAlarm>()

        allReminders.forEach { entity ->
            val medicine = entity.toMedicine()
            medicine.times.forEach { time ->
                val parts = time.split(":")
                if (parts.size == 2) {
                    val hour = parts[0].toIntOrNull() ?: return@forEach
                    val minute = parts[1].toIntOrNull() ?: return@forEach
                    val calendar = reminderManager.calculateNextOccurrence(hour, minute, medicine.repeatDays, medicine.startDate)
                    val triggerTimeMs = calendar.timeInMillis
                    if (triggerTimeMs in now..twelveHoursLater) {
                        imminentAlarms.add(ImminentAlarm(medicine, time, triggerTimeMs))
                    }
                }
            }
        }

        // Sort by trigger time ascending and take top 5
        val topImminent = imminentAlarms.sortedBy { it.triggerTimeMs }.take(5)

        Log.d("BootReceiver", "Reboot recovery: scheduling top ${topImminent.size} imminent alarms immediately.")
        topImminent.forEach { imminent ->
            reminderManager.scheduleAlarm(imminent.medicine, imminent.time, userId)
        }
    }

    private fun enqueueBootRescheduleWorker(context: Context) {
        Log.d("BootReceiver", "BootRescheduleWorker: Enqueueing deferred comprehensive scan.")
        val rescheduleWork = OneTimeWorkRequestBuilder<BootRescheduleWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        
        WorkManager.getInstance(context).enqueueUniqueWork(
            "BootRescheduleWork",
            ExistingWorkPolicy.REPLACE,
            rescheduleWork
        )
    }
}
