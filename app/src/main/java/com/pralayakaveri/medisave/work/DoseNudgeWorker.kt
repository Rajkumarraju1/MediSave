package com.pralayakaveri.medisave.work

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.model.DoseStatus
import com.pralayakaveri.medisave.reminder.AlarmReceiver
import kotlinx.coroutines.flow.first

class DoseNudgeWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val medicineId = inputData.getString("MEDICINE_ID") ?: return Result.failure()
        val medicineName = inputData.getString("MEDICINE_NAME") ?: "Medicine"
        val date = inputData.getString("REMINDER_DATE") ?: return Result.failure()
        val time = inputData.getString("REMINDER_TIME") ?: return Result.failure()

        val db = AppDatabase.getDatabase(applicationContext)
        val medicine = db.medicineReminderDao().getById(medicineId)?.toMedicine() ?: return Result.failure()

        val currentStatus = medicine.getStatusAt(date, time)
        
        if (currentStatus == DoseStatus.PENDING.name) {
            val prefManager = com.pralayakaveri.medisave.data.PreferenceManager(applicationContext)
            val pushEnabled = prefManager.pushNotificationsEnabled.first()
            if (pushEnabled) {
                showNudgeNotification(medicineName, time)
            }
        }

        return Result.success()
    }

    private fun showNudgeNotification(name: String, time: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create channel if needed
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                AlarmReceiver.NUDGE_CHANNEL_ID,
                "Medicine Nudges",
                NotificationManager.IMPORTANCE_DEFAULT // "Soft" priority
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Functional "Mark as Taken" intent
        val medicineId = inputData.getString("MEDICINE_ID")!!
        val date = inputData.getString("REMINDER_DATE")!!
        val userId = inputData.getString("USER_ID") ?: ""

        val takenIntent = android.content.Intent(applicationContext, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_MARK_TAKEN
            putExtra("USER_ID", userId)
            putExtra("MEDICINE_ID", medicineId)
            putExtra("REMINDER_TIME", time)
            putExtra("REMINDER_DATE", date)
        }

        val takenPendingIntent = android.app.PendingIntent.getBroadcast(
            applicationContext,
            (medicineId + time).hashCode() + 2, // Unique request code
            takenIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Functional "Snooze 5m" intent
        val snoozeIntent = android.content.Intent(applicationContext, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE
            putExtra("USER_ID", userId)
            putExtra("MEDICINE_ID", medicineId)
            putExtra("MEDICINE_NAME", name)
            putExtra("REMINDER_TIME", time)
            putExtra("REMINDER_DATE", date)
            putExtra("SNOOZE_DURATION", 5)
        }

        val snoozePendingIntent = android.app.PendingIntent.getBroadcast(
            applicationContext,
            (medicineId + time).hashCode() + 3, // Unique request code
            snoozeIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, AlarmReceiver.NUDGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Gentle Reminder")
            .setContentText("Hey, it’s time to take your $name")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_save, "Mark as Taken", takenPendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze 5m", snoozePendingIntent)

        // Use same ID as Alarm to REPLACE it
        val notificationId = (medicineId + time).hashCode()
        notificationManager.notify(notificationId, builder.build())
    }
}
