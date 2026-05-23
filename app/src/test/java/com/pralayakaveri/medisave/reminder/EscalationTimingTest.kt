package com.pralayakaveri.medisave.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class EscalationTimingTest {

    @Test
    fun testNudgeMappingIntervals() {
        val alarmReceiver = AlarmReceiver()

        // 5 Minutes setting
        val nudges5 = alarmReceiver.getReminderNudges(5, true)
        assertEquals(listOf(2), nudges5)

        // 10 Minutes setting
        val nudges10 = alarmReceiver.getReminderNudges(10, true)
        assertEquals(listOf(5), nudges10)

        // 15 Minutes setting
        val nudges15 = alarmReceiver.getReminderNudges(15, true)
        assertEquals(listOf(5, 10), nudges15)

        // 30 Minutes setting
        val nudges30 = alarmReceiver.getReminderNudges(30, true)
        assertEquals(listOf(10, 20), nudges30)

        // 1 Hour setting
        val nudges60 = alarmReceiver.getReminderNudges(60, true)
        assertEquals(listOf(30, 45), nudges60)

        // Never setting
        val nudgesNever = alarmReceiver.getReminderNudges(60, false)
        assertEquals(emptyList<Int>(), nudgesNever)
    }
}
