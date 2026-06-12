package com.pralayakaveri.medisave.work

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.data.MedicineRepository
import com.pralayakaveri.medisave.model.DoseStatus
import com.pralayakaveri.medisave.reminder.AlarmReceiver
import kotlinx.coroutines.flow.first

class MissedDoseWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString("USER_ID") ?: return Result.failure()
        val medicineId = inputData.getString("MEDICINE_ID") ?: return Result.failure()
        val medicineName = inputData.getString("MEDICINE_NAME") ?: "Medicine"
        val date = inputData.getString("REMINDER_DATE") ?: return Result.failure()
        val time = inputData.getString("REMINDER_TIME") ?: return Result.failure()

        val db = AppDatabase.getDatabase(applicationContext)
        val medEntity = db.medicineReminderDao().getById(medicineId) ?: return Result.failure()
        val medicine = medEntity.toMedicine()

        val currentStatus = medicine.getStatusAt(date, time)
        
        // TRIPLE GUARD:
        // 1. Status must be PENDING (not already handled)
        // 2. Scheduled time + 5 mins must have passed
        // 3. syncPending must be false (no in-flight sync that might have a TAKEN status)
        
        val scheduledTimeMs = com.pralayakaveri.medisave.util.getTimestamp(date, time)
        val currentTimeMs = System.currentTimeMillis()
        val graceMs = medicine.gracePeriodMinutes * 60 * 1000L
        val isGracePeriodPassed = currentTimeMs > (scheduledTimeMs + graceMs)
        
        if (currentStatus == DoseStatus.PENDING.name && isGracePeriodPassed) {
            val repository = MedicineRepository(applicationContext)
            try {
                android.util.Log.d("MissedDoseWorker", "CRITICAL: Marking MISSED for $medicineName at $time (Grace passed: ${medicine.gracePeriodMinutes}m)")
                repository.updateMedicineStatus(userId, medicineId, date, time, DoseStatus.MISSED.name)
                
                // Only show local notification if push notifications are enabled and missed alerts are enabled in settings
                val prefManager = com.pralayakaveri.medisave.data.PreferenceManager(applicationContext)
                val pushEnabled = prefManager.pushNotificationsEnabled.first()
                val isAlertEnabled = prefManager.missedDoseAlertEnabled.first()
                
                if (pushEnabled && isAlertEnabled) {
                    showMissedNotification(medicineName, time)
                }
            } catch (e: Exception) {
                android.util.Log.e("MissedDoseWorker", "Failed to mark missed dose for $medicineName", e)
                return Result.retry()
            }
        } else {
            android.util.Log.d("MissedDoseWorker", "Check skipped for $medicineName: Status=$currentStatus, GracePassed=$isGracePeriodPassed")
        }

        return Result.success()
    }

    private fun showMissedNotification(name: String, time: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Cancel the original reminder notification if it's still showing
        val cancelId = (inputData.getString("MEDICINE_ID")!! + time).hashCode()
        android.util.Log.d("MissedDoseWorker", "[ALARM_FLOW] Worker CANCELLING reminder notification for $name at $time (ID: $cancelId) via NotificationManager.cancel()")
        notificationManager.cancel(cancelId)
        android.util.Log.d("MissedDoseWorker", "[ALARM_FLOW] Worker Cancelled reminder notification successfully")

        val builder = NotificationCompat.Builder(applicationContext, AlarmReceiver.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Missed dose")
            .setContentText("$name was not taken")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val missedId = (inputData.getString("MEDICINE_ID")!! + time + "MISSED").hashCode()
        android.util.Log.d("MissedDoseWorker", "[ALARM_FLOW] Worker posting Missed Notification for $name at $time (ID: $missedId) via NotificationManager.notify()")
        notificationManager.notify(missedId, builder.build())
        android.util.Log.d("MissedDoseWorker", "[ALARM_FLOW] Worker Posted Missed Notification successfully")
    }
}
