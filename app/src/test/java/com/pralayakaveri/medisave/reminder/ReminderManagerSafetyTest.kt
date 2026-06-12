package com.pralayakaveri.medisave.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.pralayakaveri.medisave.model.Medicine
import io.mockk.*
import org.junit.Before
import org.junit.Test

class ReminderManagerSafetyTest {

    private val context = mockk<Context>(relaxed = true)
    private val alarmManager = mockk<AlarmManager>(relaxed = true)
    private lateinit var reminderManager: ReminderManager

    @Before
    fun setup() {
        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        
        // Mock Log
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.d(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        // Mock WorkManager
        val mockWorkManager = mockk<androidx.work.WorkManager>(relaxed = true)
        mockkStatic(androidx.work.WorkManager::class)
        every { androidx.work.WorkManager.getInstance(any()) } returns mockWorkManager

        // Mock Intent
        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<String>()) } returns mockk()
        every { anyConstructed<android.content.Intent>().setAction(any()) } returns mockk()

        // Mock static PendingIntent.getBroadcast
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns mockk(relaxed = true)
        
        reminderManager = ReminderManager(context)
    }

    @Test
    fun `cancelAlarmsForMember only cancels specific medicine alarms and does not impact others`() {
        // GIVEN: Two medicines, one for Primary user, one for Manual member
        val primaryMedicine = Medicine(
            id = "PRIMARY_MED_1",
            name = "Advil",
            times = listOf("08:00"),
            profileId = "primary"
        )
        
        val manualMedicine = Medicine(
            id = "MANUAL_MED_99",
            name = "Vitamins",
            times = listOf("08:00"),
            profileId = "MEMBER_ABC"
        )

        // WHEN: We cancel alarms for the Manual medicine
        reminderManager.cancelAlarmsForMedicine(manualMedicine)

        // THEN: Verify AlarmManager.cancel was called with the manual medicine's PendingIntent
        // We verify by checking the request code logic in createPendingIntent
        val expectedManualRequestCode = ("MANUAL_MED_99" + "08:00").hashCode()
        val expectedPrimaryRequestCode = ("PRIMARY_MED_1" + "08:00").hashCode()

        // Capture all calls to getBroadcast to verify request codes
        verify {
            PendingIntent.getBroadcast(
                any(),
                expectedManualRequestCode, // Must be called for manual
                any(),
                any()
            )
        }

        verify(exactly = 0) {
            PendingIntent.getBroadcast(
                any(),
                expectedPrimaryRequestCode, // MUST NOT be called for primary
                any(),
                any()
            )
        }
    }
}
