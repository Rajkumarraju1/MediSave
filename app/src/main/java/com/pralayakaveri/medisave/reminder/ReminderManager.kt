package com.pralayakaveri.medisave.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.pralayakaveri.medisave.model.Medicine
import java.util.*

class ReminderManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules alarms for all time slots of a medicine.
     */
    fun scheduleAlarmsForMedicine(medicine: Medicine, userId: String) {
        // Always cancel existing alarms for this medicine first to prevent duplicates
        cancelAlarmsForMedicine(medicine)

        medicine.times.forEach { time ->
            scheduleAlarm(medicine, time, userId)
        }
    }

    private fun canScheduleExact(): Boolean {
        val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = context.checkSelfPermission(android.Manifest.permission.USE_EXACT_ALARM) == android.content.pm.PackageManager.PERMISSION_GRANTED
                Log.d("ReminderManager", "USE_EXACT_ALARM check: $granted")
                granted
            } else {
                false
            }
            val canSchedule = alarmManager.canScheduleExactAlarms()
            Log.d("ReminderManager", "canScheduleExactAlarms: $canSchedule")
            hasUseExact || canSchedule
        } else {
            true
        }
        Log.d("ReminderManager", "canScheduleExact final result: $res")
        return res
    }

    fun scheduleAlarm(medicine: Medicine, time: String, userId: String) {
        val (hour, minute) = time.split(":").map { it.toInt() }
        val calendar = calculateNextOccurrence(hour, minute, medicine.repeatDays, medicine.startDate)
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)

        // Cancellation First: explicitly cancel any existing PendingIntent & Alarm first (Duplicate Alarm Prevention)
        val cancelIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("MEDICINE_ID", medicine.id)
            putExtra("REMINDER_TIME", time)
        }
        val requestCode = (medicine.id + time).hashCode()
        val existingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            cancelIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existingIntent != null) {
            alarmManager.cancel(existingIntent)
            existingIntent.cancel()
            Log.d("ReminderManager", "Cancelled existing exact alarm for ${medicine.name} at $time")
        }

        val pendingIntent = createPendingIntent(
            medicineId = medicine.id,
            time = time,
            medicineName = medicine.name,
            medicineDose = medicine.dose,
            userId = userId,
            reminderDate = dateStr
        )

        val triggerTime = calendar.timeInMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (canScheduleExact()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact if permission denied
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d("ReminderManager", "Scheduled alarm for ${medicine.name} at ${calendar.time}")
        } catch (e: Exception) {
            Log.e("ReminderManager", "Error scheduling alarm", e)
        }
    }

    fun cancelAlarmsForMedicine(medicine: Medicine) {
        medicine.times.forEach { time ->
            val pendingIntent = createPendingIntent(medicine.id, time)
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("ReminderManager", "Cancelled alarm for ${medicine.id} at $time")
        }
        // Cancel all WorkManager tasks for this medicine
        androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag("MEDICINE_ID_${medicine.id}")
        Log.d("ReminderManager", "Cancelled all background checks for ${medicine.id}")
    }

    private fun createPendingIntent(
        medicineId: String,
        time: String,
        medicineName: String? = null,
        medicineDose: String? = null,
        userId: String? = null,
        reminderDate: String? = null
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("MEDICINE_ID", medicineId)
            putExtra("REMINDER_TIME", time)
            medicineName?.let { putExtra("MEDICINE_NAME", it) }
            medicineDose?.let { putExtra("MEDICINE_DOSE", it) }
            userId?.let { putExtra("USER_ID", it) }
            reminderDate?.let { putExtra("REMINDER_DATE", it) }
        }
        val requestCode = (medicineId + time).hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Calculates the next valid occurrence of a reminder.
     * If the time has already passed today, or today is not a repeat day,
     * it finds the next available repeat day.
     */
    fun calculateNextOccurrence(hour: Int, minute: Int, repeatDays: List<Int>, startDate: String = ""): Calendar {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 1. Minimum start constraint: Ensure we don't start before the medicine's startDate
        if (startDate.isNotEmpty()) {
            try {
                val startLocal = java.time.LocalDate.parse(startDate)
                val startCal = Calendar.getInstance().apply {
                    set(startLocal.year, startLocal.monthValue - 1, startLocal.dayOfMonth, hour, minute, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (target.before(startCal)) {
                    target.timeInMillis = startCal.timeInMillis
                }
            } catch (e: Exception) {
                Log.e("ReminderManager", "Error parsing startDate: $startDate", e)
            }
        }

        // 2. Immediate past constraint: If target time (adjusted by startDate) already passed today, start from tomorrow
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        // 3. Weekday constraint: Find the next valid day from repeatDays
        while (true) {
            val calendarDay = target.get(Calendar.DAY_OF_WEEK)
            val modelDay = when (calendarDay) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }

            if (repeatDays.contains(modelDay)) {
                break
            }
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target
    }

    private fun getRequestCode(medicineId: String, time: String): Int {
        // Unique ID for each (medicine + time slot)
        return (medicineId + time).hashCode()
    }

    /**
     * Reschedules alarms for all active medicines.
     */
    suspend fun rescheduleAllAlarms(userId: String) {
        val db = com.pralayakaveri.medisave.data.AppDatabase.getDatabase(context)
        val allReminders = db.medicineReminderDao().getAllReminders()
        Log.d("ReminderManager", "Rescheduling all ${allReminders.size} alarms for user: $userId")
        allReminders.forEach { entity ->
            scheduleAlarmsForMedicine(entity.toMedicine(), userId)
        }
    }
}
