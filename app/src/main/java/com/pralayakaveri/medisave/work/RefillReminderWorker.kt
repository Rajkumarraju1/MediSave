package com.pralayakaveri.medisave.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pralayakaveri.medisave.R
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.data.PreferenceManager
import kotlinx.coroutines.flow.first
import java.util.Calendar

class RefillReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val prefManager = PreferenceManager(applicationContext)
        
        // 1. Check Global Push Toggle
        val pushEnabled = prefManager.pushNotificationsEnabled.first()
        if (!pushEnabled) return Result.success()

        // 2. Check Refill Reminder Toggle
        val refillEnabled = prefManager.refillRemindersEnabled.first()
        if (!refillEnabled) return Result.success()

        val medicines = database.medicineReminderDao().getAllReminders()
        val today = Calendar.getInstance()
        val todayStart = today.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var notifiedCount = 0

        for (med in medicines) {
            // Gate 1: Only alert when stock is LOW but not yet ZERO.
            // When pillsLeft == 0 the user already knows and the card is red —
            // daily notifications at that point are counterproductive spam.
            // Gate 2: lastRefillNotifiedAt ensures at most one alert per calendar day.
            if (med.pillsLeft in 1..med.refillAt && med.lastRefillNotifiedAt < todayStart) {
                showRefillNotification(med.name, med.pillsLeft)

                // Update timestamp locally (will trigger sync via syncPending=true in DAO)
                database.medicineReminderDao().updateRefillNotificationTime(med.id, System.currentTimeMillis())
                notifiedCount++
            }
        }

        if (notifiedCount > 0) {
            // Trigger best-effort sync
            val userId = prefManager.sessionUserId.first()
            if (userId != null) {
                val medRepo = com.pralayakaveri.medisave.data.MedicineRepository(applicationContext)
                medRepo.syncPendingMedicines(userId)
            }
        }

        return Result.success()
    }

    private fun showRefillNotification(medicineName: String, pillsLeft: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channelId = "refill_alerts"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Refill Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to refill your medication stock"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pillWord = if (pillsLeft == 1) "pill" else "pills"
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("💊 Refill Reminder: $medicineName")
            .setContentText("Only $pillsLeft $pillWord remaining. Tap to add stock before you run out.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup("refill_group")
            .build()

        notificationManager.notify(medicineName.hashCode(), notification)
    }
}
