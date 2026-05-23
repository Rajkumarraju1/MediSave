package com.pralayakaveri.medisave.util

import com.pralayakaveri.medisave.model.Medicine
import com.pralayakaveri.medisave.model.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneId

class AdherenceCalculatorTest {

    @org.junit.Before
    fun setup() {
        io.mockk.mockkStatic(android.util.Log::class)
        io.mockk.every { android.util.Log.d(any(), any()) } returns 0
    }

    @Test
    fun testAdherenceCalculation() {
        val forcedZone = ZoneId.of("Asia/Kolkata")
        val anchorTime = ZonedDateTime.parse("2026-04-24T12:00:00+05:30[Asia/Kolkata]")
        val todayStr = "2026-04-24"

        // Given: 2 medicines, each with 1 dose at 08:00 AM
        // One is taken, one is pending
        val med1 = Medicine(
            id = "med1",
            name = "Aspirin",
            times = listOf("08:00"),
            repeatDays = listOf(1, 2, 3, 4, 5, 6, 7), // Every day
            startDate = "2026-04-01",
            statusMap = mapOf("${todayStr}_08:00" to DoseStatus.TAKEN.name)
        )

        val med2 = Medicine(
            id = "med2",
            name = "Vitamin C",
            times = listOf("08:00"),
            repeatDays = listOf(1, 2, 3, 4, 5, 6, 7),
            startDate = "2026-04-01",
            statusMap = emptyMap() // Pending
        )

        val medicines = listOf(med1, med2)

        // When
        val report = AdherenceCalculator.calculateReport(
            medicines = medicines,
            anchorTime = anchorTime
        )

        // med3 is at 08:00 PM (FUTURE relative to 12:00 PM anchor)
        val med3 = Medicine(
            id = "med3",
            name = "Night Pill",
            times = listOf("20:00"),
            repeatDays = listOf(1, 2, 3, 4, 5, 6, 7),
            startDate = "2026-04-01",
            statusMap = emptyMap()
        )

        val medicinesWithFuture = listOf(med1, med2, med3)

        // When
        val reportWithFuture = AdherenceCalculator.calculateReport(
            medicines = medicinesWithFuture,
            anchorTime = anchorTime
        )

        // Then
        // At 12:00 PM, only med1 and med2 are "due" (08:00 AM doses)
        // med3 (08:00 PM) is NOT due yet, so it should NOT be in today's due denominator
        val todayResult = reportWithFuture.dailyResults[4] // Friday
        assertEquals("Today's due total should be 2 (excluding future dose)", 2, todayResult.dueSoFarTotal)
        assertEquals("Today's due taken should be 1", 1, todayResult.dueSoFarTaken)
        assertEquals("Weekly total under Option A includes all past days + today's full doses", 15, reportWithFuture.weeklyStats.total)
        assertEquals("Weekly taken should be 1", 1, reportWithFuture.weeklyStats.taken)
        assertEquals("Adherence should be 6% (1 taken out of 15 total)", 6, reportWithFuture.adherencePercentage)
    }

    @Test
    fun testFutureStartDateExclusion() {
        val forcedZone = ZoneId.of("Asia/Kolkata")
        val anchorTime = ZonedDateTime.parse("2026-04-24T12:00:00+05:30[Asia/Kolkata]")
        val todayStr = "2026-04-24"

        // Med starts tomorrow
        val med1 = Medicine(
            id = "med1",
            name = "Future Med",
            times = listOf("08:00"),
            repeatDays = listOf(1, 2, 3, 4, 5, 6, 7),
            startDate = "2026-04-25", // Starts Tomorrow
            statusMap = emptyMap()
        )

        val medicines = listOf(med1)

        // When
        val report = AdherenceCalculator.calculateReport(
            medicines = medicines,
            anchorTime = anchorTime
        )

        // Then
        assertEquals("Scheduled today should be 0", 0, report.todayStats.total)
        assertEquals("Adherence should be 0", 0, report.adherencePercentage)
    }
}
