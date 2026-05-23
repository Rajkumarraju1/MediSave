package com.pralayakaveri.medisave.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pralayakaveri.medisave.MainActivity
import com.pralayakaveri.medisave.R
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.data.MedicineRepository
import com.pralayakaveri.medisave.model.Medicine
import com.pralayakaveri.medisave.model.DoseStatus
import android.app.AlarmManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import com.pralayakaveri.medisave.work.MissedDoseWorker
import java.util.concurrent.TimeUnit
import java.util.Calendar
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "MEDISAVE_REMINDERS"
        const val NUDGE_CHANNEL_ID = "MEDISAVE_NUDGES"
        const val ACTION_MARK_TAKEN = "com.pralayakaveri.medisave.ACTION_MARK_TAKEN"
        const val ACTION_SNOOZE = "com.pralayakaveri.medisave.ACTION_SNOOZE"
        const val ACTION_CHECK_MISSED = "com.pralayakaveri.medisave.ACTION_CHECK_MISSED"
        const val ACTION_TRIGGER_NUDGE = "com.pralayakaveri.medisave.ACTION_TRIGGER_NUDGE"
        const val ACTION_TRIGGER_MISSED = "com.pralayakaveri.medisave.ACTION_TRIGGER_MISSED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val medicineId = intent.getStringExtra("MEDICINE_ID") ?: return
        val medicineName = intent.getStringExtra("MEDICINE_NAME") ?: "Medicine"
        val dose = intent.getStringExtra("MEDICINE_DOSE") ?: ""
        val userId = intent.getStringExtra("USER_ID") ?: ""
        val reminderDate = intent.getStringExtra("REMINDER_DATE") ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val reminderTime = intent.getStringExtra("REMINDER_TIME") ?: ""
        val isSnooze = intent.getBooleanExtra("IS_SNOOZE", false)
        val snoozeDuration = intent.getIntExtra("SNOOZE_DURATION", 10)

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                when (action) {
                    ACTION_MARK_TAKEN -> {
                        handleMarkAsTaken(context, userId, medicineId, reminderDate, reminderTime)
                    }
                    ACTION_SNOOZE -> {
                        handleSnooze(context, userId, medicineId, medicineName, dose, reminderDate, reminderTime, snoozeDuration)
                    }
                    ACTION_CHECK_MISSED, ACTION_TRIGGER_MISSED -> {
                        handleCheckMissed(context, userId, medicineId, medicineName, reminderDate, reminderTime)
                    }
                    ACTION_TRIGGER_NUDGE -> {
                        val stage = intent.getIntExtra("NUDGE_STAGE", 1)
                        handleNudgeCheck(context, userId, medicineId, medicineName, reminderDate, reminderTime, stage)
                    }
                    else -> {
                        // Generic alarm trigger -> Show Notification
                        showNotification(context, userId, medicineId, medicineName, dose, reminderDate, reminderTime)
                        // Reschedule next occurrence for this medicine slot if it's the main alarm
                        if (!isSnooze) {
                            rescheduleNext(context, userId, medicineId)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error in AlarmReceiver processing inside goAsync scope", e)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private fun canScheduleExact(context: Context, alarmManager: android.app.AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.USE_EXACT_ALARM) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
            hasUseExact || alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private suspend fun handleSnooze(context: Context, userId: String, medicineId: String, name: String, dose: String, date: String, time: String, durationMin: Int) {
        // Ghost-alert safety net: abort silently if medicine was deleted
        val db = AppDatabase.getDatabase(context)
        if (db.medicineReminderDao().getById(medicineId) == null) {
            Log.w("AlarmReceiver", "handleSnooze: medicine $medicineId no longer exists — suppressing snooze alarm")
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel((medicineId + time).hashCode())

        // Show Toast response to user with exact time on the Main Thread
        withContext(Dispatchers.Main) {
            val nextCal = Calendar.getInstance()
            nextCal.add(Calendar.MINUTE, durationMin)
            val timeStr = java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(nextCal.time)
            android.widget.Toast.makeText(context, "Snoozed. Next reminder at $timeStr", android.widget.Toast.LENGTH_SHORT).show()
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("USER_ID", userId)
            putExtra("MEDICINE_ID", medicineId)
            putExtra("MEDICINE_NAME", name)
            putExtra("MEDICINE_DOSE", dose)
            putExtra("REMINDER_TIME", time)
            putExtra("REMINDER_DATE", date)
            putExtra("IS_SNOOZE", true)
        }

        val requestCode = (medicineId + time + "SNOOZE").hashCode()

        // Duplicate Alarm Prevention Policy: cancel any pre-existing intent first
        val existingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            snoozeIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existingIntent != null) {
            alarmManager.cancel(existingIntent)
            existingIntent.cancel()
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + durationMin * 60 * 1000

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (canScheduleExact(context, alarmManager)) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d("AlarmReceiver", "Snooze alarm set for $durationMin mins")
            
            // Also extend the Missed Dose check to prevent premature marking
            scheduleMissedCheck(context, userId, medicineId, name, date, time, extraDelayMin = durationMin)
            
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error scheduling snooze", e)
        }
    }

    private suspend fun handleMarkAsTaken(context: Context, userId: String, medicineId: String, date: String, time: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Cancel the notification using the unique hash of medicineId + time
        notificationManager.cancel((medicineId + time).hashCode())

        if (userId.isEmpty() || time.isEmpty()) return

        try {
            // Pass application context to repository to safely hold context reference
            val repository = MedicineRepository(context.applicationContext)
            repository.updateMedicineStatus(userId, medicineId, date, time, DoseStatus.TAKEN.name)
            cancelPendingChecks(context, medicineId, date, time)
            Log.d("AlarmReceiver", "Medicine $medicineId at $time marked as taken successfully")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to mark medicine as taken", e)
        }
    }

    private fun cancelPendingChecks(context: Context, medicineId: String, date: String, time: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // 1. Cancel exact nudge alarms (up to 2 stages)
        for (stage in 1..2) {
            val nudgeIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER_NUDGE
            }
            val requestCode = (medicineId + date + time + "NUDGE_$stage").hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                nudgeIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d("AlarmReceiver", "Cancelled Exact Nudge Alarm stage $stage for $medicineId at $date $time")
            }
        }

        // 2. Cancel exact missed alarm
        val missedIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_MISSED
        }
        val missedRequestCode = (medicineId + date + time + "MISSED").hashCode()
        val missedPendingIntent = PendingIntent.getBroadcast(
            context,
            missedRequestCode,
            missedIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (missedPendingIntent != null) {
            alarmManager.cancel(missedPendingIntent)
            missedPendingIntent.cancel()
            Log.d("AlarmReceiver", "Cancelled Exact Missed Alarm for $medicineId at $date $time")
        }

        // 3. Backward compatibility: legacy WorkManager unique work cancellation
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork("${medicineId}_${date}_${time}_MISSED_CHECK")
        workManager.cancelUniqueWork("${medicineId}_${time}_NUDGE_CHECK")
        for (i in 1..2) {
            workManager.cancelUniqueWork("${medicineId}_${time}_NUDGE_CHECK_$i")
        }
        workManager.cancelAllWorkByTag("MEDICINE_ID_${medicineId}")
    }

    private suspend fun handleCheckMissed(context: Context, userId: String, medicineId: String, name: String, date: String, time: String) {
        val db = AppDatabase.getDatabase(context)
        val medicine = db.medicineReminderDao().getById(medicineId)?.toMedicine() ?: return
        
        val currentStatus = medicine.getStatusAt(date, time)
        
        val scheduledTimeMs = com.pralayakaveri.medisave.util.getTimestamp(date, time)
        val currentTimeMs = System.currentTimeMillis()
        val graceMs = medicine.gracePeriodMinutes * 60 * 1000L
        val isGracePeriodPassed = currentTimeMs >= (scheduledTimeMs + graceMs - 5000L)

        if (currentStatus == DoseStatus.PENDING.name && isGracePeriodPassed) {
            val repository = MedicineRepository(context.applicationContext)
            try {
                Log.d("AlarmReceiver", "CRITICAL: Marking MISSED for $name at $time (Grace passed: ${medicine.gracePeriodMinutes}m)")
                repository.updateMedicineStatus(userId, medicineId, date, time, DoseStatus.MISSED.name)
                
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel((medicineId + time).hashCode())
                
                // Only show local notification if push notifications are enabled and missed alerts are enabled in settings
                val prefManager = com.pralayakaveri.medisave.data.PreferenceManager(context)
                val pushEnabled = prefManager.pushNotificationsEnabled.firstOrNull() ?: true
                val isAlertEnabled = prefManager.missedDoseAlertEnabled.firstOrNull() ?: true
                
                if (pushEnabled && isAlertEnabled) {
                    showMissedNotification(context, medicineId, name, time)
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to mark missed dose for $name", e)
            }
        } else {
            Log.d("AlarmReceiver", "Check missed skipped for $name: Status=$currentStatus, GracePassed=$isGracePeriodPassed")
        }
    }

    private suspend fun showMissedNotification(context: Context, medicineId: String, name: String, time: String) {
        // Setting behavior integrity guard: abort if push notifications or missed alerts are disabled in settings
        val prefManager = com.pralayakaveri.medisave.data.PreferenceManager(context)
        val pushEnabled = prefManager.pushNotificationsEnabled.firstOrNull() ?: true
        if (!pushEnabled) {
            Log.i("AlarmReceiver", "showMissedNotification suppressed: Push Notifications are disabled in Settings")
            return
        }
        val isAlertEnabled = prefManager.missedDoseAlertEnabled.firstOrNull() ?: true
        if (!isAlertEnabled) {
            Log.i("AlarmReceiver", "showMissedNotification suppressed: Missed Dose Alerts are disabled in Settings")
            return
        }

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
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // using standard alert icon
            .setContentTitle("Missed dose")
            .setContentText("$name was not taken")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)

        notificationManager.notify((medicineId + time + "MISSED").hashCode(), builder.build())
    }

    private suspend fun showNotification(context: Context, userId: String, medicineId: String, name: String, dose: String, date: String, time: String) {
        // Setting behavior integrity guard: abort if push notifications are disabled in settings
        val prefManager = com.pralayakaveri.medisave.data.PreferenceManager(context)
        val pushEnabled = prefManager.pushNotificationsEnabled.firstOrNull() ?: true
        if (!pushEnabled) {
            Log.i("AlarmReceiver", "showNotification suppressed: Push Notifications are disabled in Settings")
            return
        }

        // Ghost-alert safety net: abort silently if medicine was deleted
        val db = AppDatabase.getDatabase(context)
        if (db.medicineReminderDao().getById(medicineId) == null) {
            Log.w("AlarmReceiver", "showNotification: medicine $medicineId no longer exists — suppressing notification")
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel if needed (Android 13+ Escalation Compliance)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily medication reminders."
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create deep-link intent to open app 
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("TARGET_MEDICINE_ID", medicineId)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            (medicineId + time).hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Functional "Mark as Taken" intent
        val takenIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MARK_TAKEN
            putExtra("USER_ID", userId)
            putExtra("MEDICINE_ID", medicineId)
            putExtra("REMINDER_TIME", time)
            putExtra("REMINDER_DATE", date)
        }

        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            (medicineId + time).hashCode() + 1, // Different request code
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time to take medicine")
            .setContentText("$name • $dose")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_save, "Mark as Taken", takenPendingIntent)

        // Add Snooze 10m
        val snooze10Intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("USER_ID", userId)
            putExtra("MEDICINE_ID", medicineId)
            putExtra("MEDICINE_NAME", name)
            putExtra("MEDICINE_DOSE", dose)
            putExtra("REMINDER_TIME", time)
            putExtra("REMINDER_DATE", date)
            putExtra("SNOOZE_DURATION", 10)
        }
        val snooze10PendingIntent = PendingIntent.getBroadcast(
            context,
            (medicineId + time + "SNOOZE10").hashCode(),
            snooze10Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_popup_reminder, "Snooze 10m", snooze10PendingIntent)

        // Add Snooze 30m
        val snooze30Intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("USER_ID", userId)
            putExtra("MEDICINE_ID", medicineId)
            putExtra("MEDICINE_NAME", name)
            putExtra("MEDICINE_DOSE", dose)
            putExtra("REMINDER_TIME", time)
            putExtra("REMINDER_DATE", date)
            putExtra("SNOOZE_DURATION", 30)
        }
        val snooze30PendingIntent = PendingIntent.getBroadcast(
            context,
            (medicineId + time + "SNOOZE30").hashCode(),
            snooze30Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_popup_reminder, "Snooze 30m", snooze30PendingIntent)

        notificationManager.notify((medicineId + time).hashCode(), builder.build())
        
        // Schedule multi-stage reminders
        scheduleNudgeCheck(context, userId, medicineId, name, date, time)
        scheduleMissedCheck(context, userId, medicineId, name, date, time)
    }

    fun getReminderNudges(graceMinutes: Int, caregiverAlertEnabled: Boolean): List<Int> {
        if (!caregiverAlertEnabled) return emptyList()
        return when (graceMinutes) {
            5 -> listOf(2)
            10 -> listOf(5)
            15 -> listOf(5, 10)
            30 -> listOf(10, 20)
            60 -> listOf(30, 45)
            else -> listOf(graceMinutes / 2) // Resilient fallback
        }
    }

    private suspend fun scheduleNudgeCheck(context: Context, userId: String, medicineId: String, name: String, date: String, time: String) {
        val db = AppDatabase.getDatabase(context)
        val medicine = db.medicineReminderDao().getById(medicineId) ?: return
        
        val nudges = getReminderNudges(medicine.gracePeriodMinutes, medicine.caregiverAlertEnabled)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        nudges.forEachIndexed { index, delayMinutes ->
            val stage = index + 1
            val nudgeIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER_NUDGE
                putExtra("USER_ID", userId)
                putExtra("MEDICINE_ID", medicineId)
                putExtra("MEDICINE_NAME", name)
                putExtra("REMINDER_DATE", date)
                putExtra("REMINDER_TIME", time)
                putExtra("NUDGE_STAGE", stage)
            }
            
            val requestCode = (medicineId + date + time + "NUDGE_$stage").hashCode()

            // Duplicate alarm prevention: cancel any existing pending intent
            val existingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                nudgeIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (existingIntent != null) {
                alarmManager.cancel(existingIntent)
                existingIntent.cancel()
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                nudgeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val scheduledTimeMs = com.pralayakaveri.medisave.util.getTimestamp(date, time)
            val triggerTime = scheduledTimeMs + delayMinutes * 60 * 1000L
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (canScheduleExact(context, alarmManager)) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
                Log.d("AlarmReceiver", "Scheduled Exact Nudge stage $stage for $name at $date $time (in ${delayMinutes}m)")
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error scheduling exact nudge stage $stage", e)
            }
        }
    }

    private suspend fun handleNudgeCheck(context: Context, userId: String, medicineId: String, name: String, date: String, time: String, stage: Int) {
        val db = AppDatabase.getDatabase(context)
        val medicine = db.medicineReminderDao().getById(medicineId)?.toMedicine() ?: return
        
        val currentStatus = medicine.getStatusAt(date, time)
        if (currentStatus == DoseStatus.PENDING.name) {
            showNudgeNotification(context, userId, medicineId, name, date, time, stage)
        }
    }

    private suspend fun showNudgeNotification(context: Context, userId: String, medicineId: String, name: String, date: String, time: String, stage: Int) {
        // Setting behavior integrity guard: abort if push notifications are disabled in settings
        val prefManager = com.pralayakaveri.medisave.data.PreferenceManager(context)
        val pushEnabled = prefManager.pushNotificationsEnabled.firstOrNull() ?: true
        if (!pushEnabled) {
            Log.i("AlarmReceiver", "showNudgeNotification suppressed: Push Notifications are disabled in Settings")
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create channel if needed (Android 13+ Escalation Compliance)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NUDGE_CHANNEL_ID,
                "Medicine Nudges",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority follow-up nudges for outstanding medicines."
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Functional "Mark as Taken" intent
        val takenIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MARK_TAKEN
            putExtra("USER_ID", userId)
            putExtra("MEDICINE_ID", medicineId)
            putExtra("REMINDER_TIME", time)
            putExtra("REMINDER_DATE", date)
        }

        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            (medicineId + time).hashCode() + 2, // Unique request code matching DoseNudgeWorker
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Functional "Snooze 5m" intent
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("USER_ID", userId)
            putExtra("MEDICINE_ID", medicineId)
            putExtra("MEDICINE_NAME", name)
            putExtra("REMINDER_TIME", time)
            putExtra("REMINDER_DATE", date)
            putExtra("SNOOZE_DURATION", 5)
        }

        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (medicineId + time).hashCode() + 3, // Unique request code matching DoseNudgeWorker
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, NUDGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Gentle Reminder")
            .setContentText("Hey, it’s time to take your $name")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_save, "Mark as Taken", takenPendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze 5m", snoozePendingIntent)

        // Use same ID as Alarm to REPLACE it
        val notificationId = (medicineId + time).hashCode()
        notificationManager.notify(notificationId, builder.build())
        Log.d("AlarmReceiver", "Nudge notification stage $stage displayed for $name")
    }

    private suspend fun scheduleMissedCheck(context: Context, userId: String, medicineId: String, name: String, date: String, time: String, extraDelayMin: Int = 0) {
        val db = AppDatabase.getDatabase(context)
        val medicine = db.medicineReminderDao().getById(medicineId) ?: return
        
        val gracePeriod = medicine.gracePeriodMinutes.toLong() + extraDelayMin
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val missedIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_MISSED
            putExtra("USER_ID", userId)
            putExtra("MEDICINE_ID", medicineId)
            putExtra("MEDICINE_NAME", name)
            putExtra("REMINDER_DATE", date)
            putExtra("REMINDER_TIME", time)
        }
        
        val requestCode = (medicineId + date + time + "MISSED").hashCode()

        // Duplicate alarm prevention: cancel any existing pending intent first
        val existingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            missedIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existingIntent != null) {
            alarmManager.cancel(existingIntent)
            existingIntent.cancel()
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            missedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val scheduledTimeMs = com.pralayakaveri.medisave.util.getTimestamp(date, time)
        val triggerTime = scheduledTimeMs + gracePeriod * 60 * 1000L
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (canScheduleExact(context, alarmManager)) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d("AlarmReceiver", "Scheduled Exact Missed Check for $name at $date $time (Grace: ${gracePeriod}m)")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error scheduling exact missed check", e)
        }
    }

    private suspend fun rescheduleNext(context: Context, userId: String, medicineId: String) {
        if (userId.isEmpty()) return
        
        val db = AppDatabase.getDatabase(context)
        val medicineEntity = db.medicineReminderDao().getById(medicineId)
        if (medicineEntity != null) {
            val reminderManager = ReminderManager(context)
            reminderManager.scheduleAlarmsForMedicine(medicineEntity.toMedicine(), userId)
        }
    }
}
