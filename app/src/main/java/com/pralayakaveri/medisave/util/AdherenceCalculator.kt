package com.pralayakaveri.medisave.util

import com.pralayakaveri.medisave.model.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Single Source of Truth for Adherence Calculation.
 * This is a pure logic engine that aggregates dose data into reports.
 */
object AdherenceCalculator {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Calculates the complete adherence report for a list of medicines.
     */
    fun calculateReport(
        medicines: List<Medicine>,
        externalLogs: Map<String, String> = emptyMap(),
        anchorTime: ZonedDateTime,
        lookbackDays: Int = 7
    ): AdherenceReport {
        val forcedZone = java.time.ZoneId.of("Asia/Kolkata")
        val zonedDateTime = anchorTime.withZoneSameInstant(forcedZone)
        val anchorDate = zonedDateTime.toLocalDate()
        
        // Calculate the Monday of the current week (for weekly bar charts)
        val mondayDate = anchorDate.minusDays((anchorDate.dayOfWeek.value - 1).toLong())
        
        val dailyResults = mutableListOf<DayResult>()
        var weeklyTaken = 0
        var weeklyTotal = 0
        var stableTaken = 0
        var stableTotal = 0
        var daysWithDataCount = 0
        
        var todayStats = AdherenceStats(0, 0, 0)
        var weeklyDueTaken = 0
        var weeklyDueTotal = 0

        // Scan 7 days (Monday to Sunday)
        for (i in 0 until 7) {
            val loopDate = mondayDate.plusDays(i.toLong())
            val dateStr = loopDate.format(dateFormatter)
            val isToday = loopDate.isEqual(anchorDate)
            val isFutureDay = loopDate.isAfter(anchorDate)
            
            var dayFullTaken = 0
            var dayFullTotal = 0
            var dayDueTaken = 0
            var dayDueTotal = 0

            medicines.forEach { med ->
                med.times.forEach { timeStr ->
                    if (ScheduleUtils.isDoseValid(med, dateStr, timeStr)) {
                        val statusKey = med.constructStatusKey(dateStr, timeStr)
                        val externalStatus = externalLogs["${med.id}_$statusKey"]
                        val internalStatus = med.statusMap[statusKey]
                        
                        val logStatus = externalStatus ?: internalStatus
                        
                        val finalStatus = when {
                            logStatus != null -> logStatus
                            else -> {
                                val time = LocalTime.parse(timeStr)
                                if (isFutureDay || (isToday && time.isAfter(zonedDateTime.toLocalTime()))) "FUTURE"
                                else "PENDING"
                            }
                        }

                        val isTaken = finalStatus.startsWith("TAKEN")
                        val isFuture = finalStatus == "FUTURE"
                        val isDueSoFar = !isFuture && (!isToday || LocalTime.parse(timeStr).isBefore(zonedDateTime.toLocalTime()))

                        dayFullTotal++
                        if (isTaken) dayFullTaken++

                        if (isDueSoFar) {
                            dayDueTotal++
                            if (isTaken) dayDueTaken++
                        }
                    }
                }
            }

            // Summary for the day
            val dayPercent = if (dayFullTotal > 0) (dayFullTaken * 100) / dayFullTotal else 0
            val dayStatus = when {
                isFutureDay -> AdherenceDayStatus.FUTURE
                dayFullTotal == 0 -> AdherenceDayStatus.EMPTY
                dayFullTaken == dayFullTotal -> AdherenceDayStatus.TAKEN
                dayFullTaken == 0 && dayDueTotal > 0 -> AdherenceDayStatus.MISSED
                else -> AdherenceDayStatus.PARTIAL
            }

            dailyResults.add(
                DayResult(
                    date = loopDate,
                    taken = dayFullTaken,
                    total = dayFullTotal,
                    dueSoFarTaken = dayDueTaken,
                    dueSoFarTotal = dayDueTotal,
                    percentage = dayPercent,
                    status = dayStatus
                )
            )

            // Aggregate weekly (Monday up to Today)
            if (!isFutureDay) {
                // OPTION A (Preferred): Always include Today's full schedule in the aggregate.
                weeklyTaken += dayFullTaken
                weeklyTotal += dayFullTotal
                
                // Accumulate weekly due so far
                weeklyDueTaken += dayDueTaken
                weeklyDueTotal += dayDueTotal

                // STABLE STATS (Excluding Today)
                if (!isToday) {
                    stableTaken += dayFullTaken
                    stableTotal += dayFullTotal
                }
                
                if (dayFullTotal > 0) daysWithDataCount++
            }

            if (isToday) {
                todayStats = AdherenceStats(dayFullTaken, dayFullTotal, dayFullTotal - dayFullTaken)
            }
        }

        val dueSoFarStats = AdherenceStats(weeklyDueTaken, weeklyDueTotal, weeklyDueTotal - weeklyDueTaken)

        val weeklyStats = AdherenceStats(weeklyTaken, weeklyTotal, weeklyTotal - weeklyTaken)
        val isDayStarted = dueSoFarStats.total > 0

        // Final Percentage (Option A)
        val finalPercentage = if (weeklyTotal > 0) (weeklyTaken * 100) / weeklyTotal else 0
        val stablePercentage = if (stableTotal > 0) (stableTaken * 100) / stableTotal else 100

        // DISPLAY LOGIC (Grace for early morning)
        val (displayPct, displayLabel) = when {
            !isDayStarted && weeklyTotal > 0 -> {
                // Before first dose: Show yesterday's percentage with a label
                stablePercentage to "Day not started yet"
            }
            else -> {
                // Normal real-time adherence
                finalPercentage to ""
            }
        }

        if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
            android.util.Log.d("ADHERENCE_DEBUG", "Final Report: Pct=$finalPercentage | Stable=$stablePercentage | DayStarted=$isDayStarted")
        }

        return AdherenceReport(
            dailyResults = dailyResults,
            todayStats = todayStats,
            dueSoFarStats = dueSoFarStats,
            weeklyStats = weeklyStats,
            daysWithData = daysWithDataCount,
            adherencePercentage = finalPercentage,
            displayPercentage = "$displayPct%",
            adherenceLabel = displayLabel,
            isDayStarted = isDayStarted,
            stablePercentage = stablePercentage
        )
    }
    
    
    /**
     * Centralized Streak Calculation Engine.
     */
    fun calculateStreak(medicines: List<Medicine>, anchorDate: LocalDate, lookbackDays: Int = 30): Int {
        var streak = 0
        // We scan backwards from yesterday
        for (i in 1..lookbackDays) {
            val scanDate = anchorDate.minusDays(i.toLong())
            val dateStr = scanDate.format(dateFormatter)
            
            var dayTotal = 0
            var dayTaken = 0
            
            medicines.forEach { med ->
                med.times.forEach { time ->
                    if (ScheduleUtils.isDoseValid(med, dateStr, time)) {
                        dayTotal++
                        if (med.getStatusAt(dateStr, time).startsWith("TAKEN")) {
                            dayTaken++
                        }
                    }
                }
            }
            
            if (dayTotal > 0) {
                if (dayTaken == dayTotal) streak++ else break
            }
        }
        return streak
    }

    private fun todayFullTotal(stats: AdherenceStats): Int = stats.total
}
