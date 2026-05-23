package com.pralayakaveri.medisave.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.data.MedicineRepository
import com.pralayakaveri.medisave.model.DoseStatus
import com.pralayakaveri.medisave.reminder.ReminderManager
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import androidx.core.app.NotificationCompat

class BootRescheduleWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("BootRescheduleWorker", "Starting comprehensive background rescheduling scan.")
        val context = applicationContext
        val db = AppDatabase.getDatabase(context)
        val reminderManager = ReminderManager(context)
        val repository = MedicineRepository(context)

        return try {
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
            Log.d("BootRescheduleWorker", "Resolved userId: $userId. Processing ${allReminders.size} medicines.")

            allReminders.forEach { entity ->
                val medicine = entity.toMedicine()
                
                // 1. Reschedule all alarms for this medicine (re-registers future exact alarms)
                reminderManager.scheduleAlarmsForMedicine(medicine, userId)

                // 2. Overdue catch-up math formula check
                val now = System.currentTimeMillis()
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                medicine.times.forEach { time ->
                    val scheduledTimeMs = com.pralayakaveri.medisave.util.getTimestamp(todayStr, time)
                    val graceMs = medicine.gracePeriodMinutes * 60 * 1000L

                    // If scheduled time + grace period has passed, and the status is PENDING
                    if (scheduledTimeMs + graceMs < now) {
                        val status = medicine.getStatusAt(todayStr, time)
                        if (status == DoseStatus.PENDING.name) {
                            try {
                                Log.d("BootRescheduleWorker", "Catch-up: Marking ${medicine.name} at $time as MISSED (scheduledTime: $scheduledTimeMs, graceMs: $graceMs, now: $now)")
                                repository.updateMedicineStatus(userId, medicine.id, todayStr, time, DoseStatus.MISSED.name)
                                
                                // Trigger High-Priority Missed Notification
                                showCatchUpMissedNotification(context, medicine.id, medicine.name, time)
                            } catch (e: Exception) {
                                Log.e("BootRescheduleWorker", "Catch-up update failed for ${medicine.name}", e)
                            }
                        }
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("BootRescheduleWorker", "Failed to complete reschedule scan", e)
            Result.retry()
        }
    }

    private fun showCatchUpMissedNotification(context: Context, medicineId: String, name: String, time: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val missedChannelId = "MEDISAVE_MISSED"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                missedChannelId,
                "Missed Dose Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for missed medications."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, missedChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Missed Dose (Catch-up)")
            .setContentText("$name scheduled for $time was missed during device downtime.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)

        notificationManager.notify((medicineId + time + "MISSED").hashCode(), builder.build())
    }
}
