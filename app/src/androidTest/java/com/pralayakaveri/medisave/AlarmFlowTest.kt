package com.pralayakaveri.medisave

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.data.MedicineReminderEntity
import com.pralayakaveri.medisave.reminder.AlarmReceiver
import com.pralayakaveri.medisave.model.Medicine
import com.pralayakaveri.medisave.model.DoseStatus
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AlarmFlowTest {

    @Test
    fun runAlarmFlowSequence() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = AppDatabase.getDatabase(context)
            
            // 1. Insert dummy medicine reminder configured with a 5-minute grace period
            val medicineId = "test_alarm_flow_med"
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val timeStr = "08:00" // Slot
            
            val medicine = MedicineReminderEntity(
                id = medicineId,
                name = "TestMed",
                dose = "1 pill",
                times = listOf(timeStr),
                instruction = "Take daily",
                statusMap = mapOf("${todayStr}_${timeStr}" to DoseStatus.PENDING.name),
                totalTaken = 0,
                totalMissed = 0,
                totalScheduled = 1,
                pillsLeft = 10,
                totalStock = 10,
                isStockInferred = false,
                lastUpdated = System.currentTimeMillis(),
                syncPending = false,
                doseQuantity = 1,
                refillAt = 2,
                colorHex = "#FF0000",
                repeatDays = listOf(1, 2, 3, 4, 5, 6, 7), // Every day
                timezone = "Asia/Kolkata",
                gracePeriodMinutes = 5,
                caregiverAlertEnabled = true
            )
            
            db.medicineReminderDao().insert(medicine)
            android.util.Log.d("AlarmFlowTest", "Inserted test medicine: $medicineId")

            val alarmReceiver = AlarmReceiver()

            // 2. Trigger Primary Reminder
            android.util.Log.d("AlarmFlowTest", ">>> SIMULATING PRIMARY REMINDER <<<")
            val primaryIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = null // Primary alarm action is null in AlarmReceiver
                putExtra("MEDICINE_ID", medicineId)
                putExtra("MEDICINE_NAME", "TestMed")
                putExtra("MEDICINE_DOSE", "1 pill")
                putExtra("USER_ID", "test_user_id")
                putExtra("REMINDER_TIME", timeStr)
                putExtra("REMINDER_DATE", todayStr)
            }
            alarmReceiver.onReceive(context, primaryIntent)

            // Give it 1 second to process async work in goAsync scope
            kotlinx.coroutines.delay(1000)

            // 3. Trigger Gentle Nudge Alarm
            android.util.Log.d("AlarmFlowTest", ">>> SIMULATING GENTLE NUDGE ALARM <<<")
            val nudgeIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_TRIGGER_NUDGE
                putExtra("MEDICINE_ID", medicineId)
                putExtra("MEDICINE_NAME", "TestMed")
                putExtra("USER_ID", "test_user_id")
                putExtra("REMINDER_TIME", timeStr)
                putExtra("REMINDER_DATE", todayStr)
                putExtra("NUDGE_STAGE", 1)
            }
            alarmReceiver.onReceive(context, nudgeIntent)

            // Give it 1 second to process async work
            kotlinx.coroutines.delay(1000)

            // 4. Trigger Missed Dose Alarm
            android.util.Log.d("AlarmFlowTest", ">>> SIMULATING MISSED ALARM <<<")
            val missedIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_TRIGGER_MISSED
                putExtra("MEDICINE_ID", medicineId)
                putExtra("MEDICINE_NAME", "TestMed")
                putExtra("USER_ID", "test_user_id")
                putExtra("REMINDER_TIME", timeStr)
                putExtra("REMINDER_DATE", todayStr)
            }
            alarmReceiver.onReceive(context, missedIntent)
            
            // Give it 1 second to process async work
            kotlinx.coroutines.delay(1000)
            
            android.util.Log.d("AlarmFlowTest", "AlarmFlowTest execution completed successfully.")
        }
    }
}
