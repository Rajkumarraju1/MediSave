package com.pralayakaveri.medisave.model

import java.time.LocalDate

/**
 * Clean data models for the Adherence Engine.
 * These are stateless and agnostic of UI or Database layers.
 */

data class AdherenceReport(
    val dailyResults: List<DayResult>,
    val todayStats: AdherenceStats,       // Full Day context (Boxes)
    val dueSoFarStats: AdherenceStats,   // Medical context (Timeline)
    val weeklyStats: AdherenceStats,     // Aggregate context (Percentage)
    val daysWithData: Int,
    val adherencePercentage: Int?,
    val displayPercentage: String,
    val adherenceLabel: String = "",
    val isDayStarted: Boolean = false,
    val stablePercentage: Int = 0        // Percentage excluding Today
)

data class DayResult(
    val date: LocalDate,
    val taken: Int,
    val total: Int,
    val dueSoFarTaken: Int,
    val dueSoFarTotal: Int,
    val percentage: Int,
    val status: AdherenceDayStatus
)

data class AdherenceStats(
    val taken: Int,
    val total: Int,
    val pending: Int
)

enum class MemberType {
    PRIMARY, CONNECTED
}

enum class AdherenceDayStatus {
    TAKEN,    // Perfect day
    MISSED,   // At least one missed, none taken
    PARTIAL,  // Some taken, some missed
    FUTURE,   // Doses scheduled for the future
    EMPTY,    // No doses scheduled
    TODAY,    // Active day
    BLUE      // New User / Safety / No Risk
}
