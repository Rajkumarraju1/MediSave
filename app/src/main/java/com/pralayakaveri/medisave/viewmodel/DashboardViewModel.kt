package com.pralayakaveri.medisave.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pralayakaveri.medisave.data.MedicineRepository
import com.pralayakaveri.medisave.model.Medicine
import com.pralayakaveri.medisave.util.AdherenceCalculator
import com.pralayakaveri.medisave.util.ScheduleUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.time.LocalTime

data class DashboardUiState(
    val todayProgress: Float = 0f,
    val globalStreak: Int = 0,
    val weeklyAdherence: Int = 0,
    val totalTaken: Int = 0,
    val totalMissed: Int = 0,
    val lastUpdated: String = "",
    val isEmpty: Boolean = true,
    val motivationalMessage: String = "Let's start your health journey!",
    val dailyResults: List<com.pralayakaveri.medisave.model.DayResult> = emptyList(),
    val monthlyTaken: Int = 0,
    val monthlyTotal: Int = 0,
    val longestStreak: Int = 0,
    val insights: List<String> = emptyList(),
    val riskAlert: String? = null,
    val todayTaken: Int = 0,
    val todayTotal: Int = 0,
    val nextReminderTime: String = "8:00 PM",
    val nextReminderDay: String = "Today",
    val missedDoseRisk: String = "LOW",
    val missedDoseRiskExplanation: String = "You're doing great!",
    val adherenceStatus: String = "On Track",
    val adherenceExplanation: String = "No recent adherence issues",
    val refillStatus: String = "Stock Healthy",
    val refillExplanation: String = "No refill required",
    val recentMissedCount: Int = 0,
    val recentActivities: List<ActivityLog> = emptyList(),
    val monthlyOverdueCount: Int = 0,
    val monthlyBestStreak: Int = 0,
    val monthOverMonthImprovement: Int = 0,
    val eveningMissPercent: Int = 0,
    val thisWeekStats: PeriodStats = PeriodStats(),
    val lastWeekStats: PeriodStats = PeriodStats(),
    val last30DaysStats: PeriodStats = PeriodStats(),
    val historyActivities: List<ActivityLog> = emptyList(),
    val medicines: List<com.pralayakaveri.medisave.model.Medicine> = emptyList()
)

enum class TrendPeriod {
    THIS_WEEK,
    LAST_WEEK,
    LAST_30_DAYS
}

data class PeriodStats(
    val adherencePercentage: Int = 0,
    val takenCount: Int = 0,
    val overdueCount: Int = 0,
    val dailyResults: List<com.pralayakaveri.medisave.model.DayResult> = emptyList()
)

data class ActivityLog(
    val id: String,
    val medicineId: String,
    val medicineName: String,
    val status: String, // "Taken", "Overdue", "Missed"
    val time: String,
    val dateLabel: String,
    val timestamp: Long,
    val actualCompletionTime: String? = null,
    val overdueDuration: String? = null,
    val delayFromScheduled: String? = null,
    val scheduledDate: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val medRepo = MedicineRepository(application)
    private val prefManager = com.pralayakaveri.medisave.data.PreferenceManager(application)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        startDataSubscription()
    }

    private fun startDataSubscription() {
        viewModelScope.launch {
            medRepo.getMedicinesFlowLocalByProfile("primary").collect { medicines ->
                // Protection against duplicates
                val distinctMeds = medicines.distinctBy { it.id }
                calculateStats(distinctMeds)
            }
        }
    }

    private suspend fun calculateStats(medicines: List<Medicine>) {
        if (medicines.isEmpty()) {
            _uiState.value = DashboardUiState(isEmpty = true)
            return
        }

        try {
            val forcedZone = java.time.ZoneId.of("Asia/Kolkata")
            val anchorTime = java.time.ZonedDateTime.now(forcedZone)
            val todayDate = anchorTime.toLocalDate()
            val thisMonday = todayDate.minusDays((todayDate.dayOfWeek.value - 1).toLong())

            // 1. Calculate Period Stats for all three periods
            val thisWeekReport = AdherenceCalculator.calculateReportForRange(
                medicines = medicines,
                anchorTime = anchorTime,
                startDate = thisMonday,
                endDate = thisMonday.plusDays(6)
            )
            val thisWeekStats = PeriodStats(
                adherencePercentage = thisWeekReport.adherencePercentage ?: 0,
                takenCount = thisWeekReport.weeklyStats.taken,
                overdueCount = thisWeekReport.weeklyStats.pending,
                dailyResults = thisWeekReport.dailyResults
            )

            val lastMonday = thisMonday.minusDays(7)
            val lastWeekReport = AdherenceCalculator.calculateReportForRange(
                medicines = medicines,
                anchorTime = anchorTime,
                startDate = lastMonday,
                endDate = lastMonday.plusDays(6)
            )
            val lastWeekStats = PeriodStats(
                adherencePercentage = lastWeekReport.adherencePercentage ?: 0,
                takenCount = lastWeekReport.weeklyStats.taken,
                overdueCount = lastWeekReport.weeklyStats.pending,
                dailyResults = lastWeekReport.dailyResults
            )

            val last30DaysReport = AdherenceCalculator.calculateReportForRange(
                medicines = medicines,
                anchorTime = anchorTime,
                startDate = todayDate.minusDays(29),
                endDate = todayDate
            )
            val last30DaysStats = PeriodStats(
                adherencePercentage = last30DaysReport.adherencePercentage ?: 0,
                takenCount = last30DaysReport.weeklyStats.taken,
                overdueCount = last30DaysReport.weeklyStats.pending,
                dailyResults = last30DaysReport.dailyResults
            )

            // For backward compatibility: Use thisWeekReport as the default "report"
            val report = thisWeekReport

            // 1. Today Progress
            val todayProgress = if (report.todayStats.total > 0) 
                report.todayStats.taken.toFloat() / report.todayStats.total 
            else 0f

            // 2. Global Streak
            val globalStreak = AdherenceCalculator.calculateStreak(medicines, todayDate)

            // 3. Weekly Adherence
            val weeklyAdherence = report.adherencePercentage ?: 0

            // 4. Totals (Historical)
            val totalTaken = medicines.sumOf { it.totalTaken }
            val totalMissed = medicines.sumOf { it.totalMissed }

            // 5. Last Updated
            val lastUpdated = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

            // 6. Motivation
            val message = when {
                weeklyAdherence >= 95 -> "Outstanding! You're a medication pro! 🏆"
                weeklyAdherence >= 80 -> "Great job! Keep this consistency up! 🌟"
                weeklyAdherence >= 50 -> "You're doing okay, but let's aim higher! 💪"
                else -> "Every dose counts. Let's start fresh today! ❤️"
            }

            // 7. Monthly Adherence and Longest Streak calculations (No DB schema changes)
            var monthlyOverdueCount = 0
            var monthlyTaken = 0
            var monthlyTotal = 0
            val last30Days = (0 until 30).map { todayDate.minusDays(it.toLong()) }
            medicines.forEach { med ->
                last30Days.forEach { date ->
                    val dateStr = date.toString()
                    med.times.forEach { time ->
                        if (ScheduleUtils.isDoseValid(med, dateStr, time)) {
                            val isFuture = dateStr == todayDate.toString() && LocalTime.parse(time).isAfter(anchorTime.toLocalTime())
                            if (!isFuture) {
                                val status = med.getStatusAt(dateStr, time)
                                if (status.startsWith("TAKEN")) {
                                    monthlyTaken++
                                    monthlyTotal++
                                } else if (status == "MISSED" || status == "PENDING" || status == "PARTIAL") {
                                    monthlyTotal++
                                    monthlyOverdueCount++
                                }
                            }
                        }
                    }
                }
            }

            // Longest Streak over 90 days backwards
            var maxStreak = 0
            var currentStreak = 0
            for (i in 1..90) {
                val scanDate = todayDate.minusDays(i.toLong())
                val dateStr = scanDate.toString()
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
                    if (dayTaken == dayTotal) {
                        currentStreak++
                    } else {
                        if (currentStreak > maxStreak) {
                            maxStreak = currentStreak
                        }
                        currentStreak = 0
                    }
                }
            }
            if (currentStreak > maxStreak) {
                maxStreak = currentStreak
            }
            val longestStreak = maxOf(maxStreak, globalStreak)

            // Monthly Best Streak strictly in 30 days window
            var maxMonthlyStreak = 0
            var currentMonthlyStreak = 0
            for (i in 29 downTo 0) {
                val scanDate = todayDate.minusDays(i.toLong())
                val dateStr = scanDate.toString()
                var dayTotal = 0
                var dayTaken = 0
                
                medicines.forEach { med ->
                    med.times.forEach { time ->
                        if (ScheduleUtils.isDoseValid(med, dateStr, time)) {
                            val isFuture = dateStr == todayDate.toString() && LocalTime.parse(time).isAfter(anchorTime.toLocalTime())
                            if (!isFuture) {
                                dayTotal++
                                if (med.getStatusAt(dateStr, time).startsWith("TAKEN")) {
                                    dayTaken++
                                }
                            }
                        }
                    }
                }
                
                if (dayTotal > 0) {
                    if (dayTaken == dayTotal) {
                        currentMonthlyStreak++
                    } else {
                        if (currentMonthlyStreak > maxMonthlyStreak) {
                            maxMonthlyStreak = currentMonthlyStreak
                        }
                        currentMonthlyStreak = 0
                    }
                }
            }
            if (currentMonthlyStreak > maxMonthlyStreak) {
                maxMonthlyStreak = currentMonthlyStreak
            }
            val monthlyBestStreak = maxMonthlyStreak

            // Month over Month calculation (days 1-30 vs days 31-60)
            var prevMonthTaken = 0
            var prevMonthTotal = 0
            for (i in 30 until 60) {
                val scanDate = todayDate.minusDays(i.toLong())
                val dateStr = scanDate.toString()
                medicines.forEach { med ->
                    med.times.forEach { time ->
                        if (ScheduleUtils.isDoseValid(med, dateStr, time)) {
                            prevMonthTotal++
                            if (med.getStatusAt(dateStr, time).startsWith("TAKEN")) {
                                prevMonthTaken++
                            }
                        }
                    }
                }
            }
            val currentMonthAdherence = if (monthlyTotal > 0) {
                (monthlyTaken.toFloat() / monthlyTotal * 100).toInt()
            } else 0
            val prevMonthAdherence = if (prevMonthTotal > 0) {
                (prevMonthTaken.toFloat() / prevMonthTotal * 100).toInt()
            } else 0
            val monthOverMonthImprovement = currentMonthAdherence - prevMonthAdherence

            // 8. Constructive Insights
            val weekdayTaken = IntArray(7)
            val weekdayTotal = IntArray(7)
            var morningTaken = 0
            var morningTotal = 0
            var eveningTaken = 0
            var eveningTotal = 0

            last30Days.forEach { date ->
                val dateStr = date.toString()
                val dayOfWeek = date.dayOfWeek.value - 1 // 0..6
                medicines.forEach { med ->
                    med.times.forEach { timeStr ->
                        if (ScheduleUtils.isDoseValid(med, dateStr, timeStr)) {
                            val isFuture = dateStr == todayDate.toString() && LocalTime.parse(timeStr).isAfter(anchorTime.toLocalTime())
                            if (!isFuture) {
                                val status = med.getStatusAt(dateStr, timeStr)
                                val isTaken = status.startsWith("TAKEN")
                                val isPast = status == "MISSED" || status == "PARTIAL" || (date != todayDate && status == "PENDING")
                                
                                if (isTaken) {
                                    weekdayTaken[dayOfWeek]++
                                    weekdayTotal[dayOfWeek]++
                                } else if (isPast) {
                                    weekdayTotal[dayOfWeek]++
                                }

                                val time = LocalTime.parse(timeStr)
                                val isMorning = time.isBefore(LocalTime.NOON)
                                if (isMorning) {
                                    if (isTaken) morningTaken++
                                    if (isTaken || isPast) morningTotal++
                                } else {
                                    if (isTaken) eveningTaken++
                                    if (isTaken || isPast) eveningTotal++
                                }
                            }
                        }
                    }
                }
            }

            var bestDayIdx = -1
            var bestPct = -1f
            for (i in 0 until 7) {
                if (weekdayTotal[i] > 0) {
                    val pct = weekdayTaken[i].toFloat() / weekdayTotal[i]
                    if (pct > bestPct) {
                        bestPct = pct
                        bestDayIdx = i
                    }
                }
            }

            val insightsList = mutableListOf<String>()
            if (bestDayIdx != -1 && bestPct > 0.0f && weekdayTotal[bestDayIdx] >= 3) {
                val bestDayName = java.time.DayOfWeek.of(bestDayIdx + 1).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                insightsList.add("$bestDayName is your strongest adherence day.")
            }

            var eveningMissPercent = 0
            if (morningTotal >= 5 && eveningTotal >= 5) {
                val morningAdherence = morningTaken.toFloat() / morningTotal
                val eveningAdherence = eveningTaken.toFloat() / eveningTotal
                val diff = morningAdherence - eveningAdherence
                if (diff >= 0.15f) {
                    eveningMissPercent = Math.round(diff * 100)
                    insightsList.add("Evening doses are missed ${eveningMissPercent}% more often than mornings.")
                    insightsList.add("Recommendation: Consider moving evening reminders 30 minutes earlier.")
                } else if (diff <= -0.15f) {
                    val morningMissPercent = Math.round(-diff * 100)
                    insightsList.add("Morning doses are missed ${morningMissPercent}% more often than evenings.")
                    insightsList.add("Recommendation: Try placing your medications near your morning routine (e.g., toothbrush).")
                }
            }

            if (monthOverMonthImprovement > 0) {
                insightsList.add("You improved ${monthOverMonthImprovement}% compared to last month.")
            } else if (monthOverMonthImprovement < 0) {
                insightsList.add("Your adherence decreased ${-monthOverMonthImprovement}% compared to last month.")
            }

            // 9. Prioritized Risk Alerts & Timeline (Consecutive Misses > Out of Stock > Critical Low > Refill Soon)
            data class PastDose(val dateTime: java.time.LocalDateTime, val isTaken: Boolean, val status: String, val medName: String)
            val pastDoses = mutableListOf<PastDose>()
            for (i in 0..7) {
                val scanDate = todayDate.minusDays(i.toLong())
                val dateStr = scanDate.toString()
                medicines.forEach { med ->
                    med.times.forEach { time ->
                        if (ScheduleUtils.isDoseValid(med, dateStr, time)) {
                            val isFuture = dateStr == todayDate.toString() && LocalTime.parse(time).isAfter(anchorTime.toLocalTime())
                            if (!isFuture) {
                                val status = med.getStatusAt(dateStr, time)
                                val isTaken = status.startsWith("TAKEN")
                                val dt = scanDate.atTime(LocalTime.parse(time))
                                pastDoses.add(PastDose(dt, isTaken, status, med.name))
                            }
                        }
                    }
                }
            }
            pastDoses.sortByDescending { it.dateTime }
            var consecutiveMissedCount = 0
            var firstMissedName = ""
            for (dose in pastDoses) {
                if (!dose.isTaken && (dose.status == "MISSED" || dose.status == "PENDING")) {
                    consecutiveMissedCount++
                    if (firstMissedName.isEmpty()) firstMissedName = dose.medName
                } else if (dose.isTaken) {
                    break
                }
            }

            var resolvedAlert: String? = null
            if (consecutiveMissedCount >= 2) {
                resolvedAlert = "You missed the last $consecutiveMissedCount scheduled doses (including $firstMissedName). Please take care to log your medications."
            } else {
                val criticalOut = medicines.firstOrNull { it.pillsLeft == 0 }
                val criticalLow = medicines.firstOrNull { it.pillsLeft > 0 && it.pillsLeft < it.doseQuantity }
                val refillSoon = medicines.firstOrNull { it.pillsLeft >= it.doseQuantity && it.pillsLeft <= it.refillAt }
                
                if (criticalOut != null) {
                    resolvedAlert = "Out of Stock: ${criticalOut.name} has 0 pills remaining. Refill needed."
                } else if (criticalLow != null) {
                    resolvedAlert = "Critical Stock: ${criticalLow.name} has only ${criticalLow.pillsLeft} pill${if (criticalLow.pillsLeft != 1) "s" else ""} left (requires ${criticalLow.doseQuantity} per dose)."
                } else if (refillSoon != null) {
                    resolvedAlert = "Refill Soon: ${refillSoon.name} is low on stock (${refillSoon.pillsLeft} pill${if (refillSoon.pillsLeft != 1) "s" else ""} remaining)."
                }
            }

            val nextCheckTs = medicines.mapNotNull { if (it.nextCheckAt > 0) it.nextCheckAt else null }.minOrNull()
            var calculatedTime = "8:00 PM"
            var calculatedDay = "Today"
            if (nextCheckTs != null) {
                try {
                    val reminderInstant = java.time.Instant.ofEpochMilli(nextCheckTs)
                    val reminderDt = java.time.ZonedDateTime.ofInstant(reminderInstant, forcedZone)
                    calculatedTime = reminderDt.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                    val diffDays = java.time.temporal.ChronoUnit.DAYS.between(anchorTime.toLocalDate(), reminderDt.toLocalDate())
                    calculatedDay = when {
                        diffDays == 0L -> "Today"
                        diffDays == 1L -> "Tomorrow"
                        else -> reminderDt.format(java.time.format.DateTimeFormatter.ofPattern("EEEE"))
                    }
                } catch (e: Exception) { }
            }
            
            val riskLevel = when {
                weeklyAdherence >= 85 -> "LOW"
                weeklyAdherence >= 60 -> "MEDIUM"
                else -> "HIGH"
            }

            val criticalOut = medicines.firstOrNull { it.pillsLeft == 0 }
            val criticalLow = medicines.firstOrNull { it.pillsLeft > 0 && it.pillsLeft < it.doseQuantity }

            // Recent adherence behavior calculations (past 7 days)
            val recentMissedCount = pastDoses.count { !it.isTaken && (it.status == "MISSED" || it.status == "PENDING") }
            val calculatedAdherenceStatus = when {
                consecutiveMissedCount >= 3 -> "Action Required"
                consecutiveMissedCount == 2 -> "Needs Attention"
                recentMissedCount > 0 -> "Needs Attention"
                else -> "On Track"
            }
            val calculatedAdherenceExplanation = when {
                consecutiveMissedCount >= 3 -> "$consecutiveMissedCount consecutive doses missed"
                consecutiveMissedCount == 2 -> "2 consecutive doses missed"
                recentMissedCount > 0 -> "$recentMissedCount dose${if (recentMissedCount > 1) "s" else ""} missed this week"
                else -> "No recent adherence issues"
            }

            val riskExplanation = when {
                consecutiveMissedCount >= 2 -> "$consecutiveMissedCount consecutive doses missed"
                criticalOut != null -> "Medication stock empty"
                criticalLow != null -> "Medication stock critically low"
                recentMissedCount > 0 -> "$recentMissedCount missed dose${if (recentMissedCount != 1) "s" else ""} this week"
                weeklyAdherence < 85 -> "Consistency needs improvement"
                else -> "You're doing great!"
            }

            // Dynamic refill warnings identifying the medicine by name
            val critOut = medicines.firstOrNull { it.pillsLeft == 0 }
            val critLow = medicines.firstOrNull { it.pillsLeft > 0 && it.pillsLeft < it.doseQuantity }
            val refSoon = medicines.firstOrNull { it.pillsLeft >= it.doseQuantity && it.pillsLeft <= it.refillAt }
            val calculatedRefillStatus = when {
                critOut != null || critLow != null -> "Refill Required"
                refSoon != null -> "Refill Soon"
                else -> "Stock Healthy"
            }
            val calculatedRefillExplanation = when {
                critOut != null -> "${critOut.name} stock critically low"
                critLow != null -> "${critLow.name} stock critically low"
                refSoon != null -> "${refSoon.name} needs refill soon"
                else -> {
                    val lowestStockMed = medicines.minByOrNull { med ->
                        val dosesPerDay = med.times.size
                        val dailyDoseQty = med.doseQuantity * dosesPerDay
                        if (dailyDoseQty > 0) med.pillsLeft.toFloat() / dailyDoseQty else Float.MAX_VALUE
                    }
                    if (lowestStockMed != null) {
                        val dosesPerDay = lowestStockMed.times.size
                        val dailyDoseQty = lowestStockMed.doseQuantity * dosesPerDay
                        val daysRemaining = if (dailyDoseQty > 0) lowestStockMed.pillsLeft / dailyDoseQty else 12
                        "Lowest stock: ${lowestStockMed.name} ($daysRemaining days remaining)"
                    } else {
                        "12 days of stock remaining"
                    }
                }
            }
            
            val db = com.pralayakaveri.medisave.data.AppDatabase.getDatabase(getApplication())
            val logs = db.doseLogDao().getLogsFromDate(todayDate.minusDays(30).toString())
            val logsMap = logs.associateBy { "${it.medicineId}_${it.date}_${it.time}" }

            // 10. Map recent activities for the timeline (past 7 days)
            val recentActivities = mutableListOf<ActivityLog>()
            for (i in 0 until 7) {
                val scanDate = todayDate.minusDays(i.toLong())
                val dateStr = scanDate.toString()
                medicines.forEach { med ->
                    med.times.forEach { timeStr ->
                        if (ScheduleUtils.isDoseValid(med, dateStr, timeStr)) {
                            val isFuture = dateStr == todayDate.toString() && LocalTime.parse(timeStr).isAfter(anchorTime.toLocalTime())
                            if (!isFuture) {
                                val statusKey = med.getStatusAt(dateStr, timeStr)
                                val isTaken = statusKey.startsWith("TAKEN")
                                val status = if (isTaken) "Taken" else if (dateStr == todayDate.toString()) "Overdue" else "Missed"
                                
                                val time = LocalTime.parse(timeStr)
                                val formattedTime = time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
                                val dateLabel = when (i) {
                                    0 -> "Today"
                                    1 -> "Yesterday"
                                    else -> scanDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
                                }
                                val timestamp = scanDate.atTime(time).atZone(forcedZone).toInstant().toEpochMilli()
                                
                                val logKey = "${med.id}_${dateStr}_${timeStr}"
                                val matchingLog = logsMap[logKey]

                                val actualCompletionTime = if (matchingLog != null && matchingLog.status.startsWith("TAKEN")) {
                                    val localTime = java.time.Instant.ofEpochMilli(matchingLog.lastUpdatedAt)
                                        .atZone(forcedZone)
                                        .toLocalTime()
                                    localTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
                                } else null

                                val delayFromScheduled = if (matchingLog != null && matchingLog.status.startsWith("TAKEN")) {
                                    val delayMs = matchingLog.lastUpdatedAt - timestamp
                                    val totalMinutes = Math.abs(delayMs) / (60 * 1000)
                                    val hours = totalMinutes / 60
                                    val mins = totalMinutes % 60
                                    val durationStr = when {
                                        hours > 0 -> "${hours}h ${mins}m"
                                        else -> "${mins}m"
                                    }
                                    if (delayMs >= 60 * 1000) {
                                        "$durationStr late"
                                    } else if (delayMs <= -60 * 1000) {
                                        "$durationStr early"
                                    } else {
                                        "On time"
                                    }
                                } else null

                                val overdueDuration = if (status == "Overdue") {
                                    val nowMs = anchorTime.toInstant().toEpochMilli()
                                    val delayMs = nowMs - timestamp
                                    if (delayMs > 0) {
                                        val totalMinutes = delayMs / (60 * 1000)
                                        val hours = totalMinutes / 60
                                        val mins = totalMinutes % 60
                                        when {
                                            hours > 0 -> "${hours}h ${mins}m"
                                            else -> "${mins}m"
                                        }
                                    } else null
                                } else null

                                recentActivities.add(
                                    ActivityLog(
                                        id = "${med.id}_${dateStr}_${timeStr}",
                                        medicineId = med.id,
                                        medicineName = med.name,
                                        status = status,
                                        time = formattedTime,
                                        dateLabel = dateLabel,
                                        timestamp = timestamp,
                                        actualCompletionTime = actualCompletionTime,
                                        overdueDuration = overdueDuration,
                                        delayFromScheduled = delayFromScheduled,
                                        scheduledDate = dateStr
                                    )
                                )
                            }
                        }
                    }
                }
            }
            recentActivities.sortByDescending { it.timestamp }

            // 11. Map history activities for the chronological log screen (past 30 days)
            val historyActivities = mutableListOf<ActivityLog>()
            for (i in 0 until 30) {
                val scanDate = todayDate.minusDays(i.toLong())
                val dateStr = scanDate.toString()
                medicines.forEach { med ->
                    med.times.forEach { timeStr ->
                        if (ScheduleUtils.isDoseValid(med, dateStr, timeStr)) {
                            val isFuture = dateStr == todayDate.toString() && LocalTime.parse(timeStr).isAfter(anchorTime.toLocalTime())
                            if (!isFuture) {
                                val statusKey = med.getStatusAt(dateStr, timeStr)
                                val isTaken = statusKey.startsWith("TAKEN")
                                val status = if (isTaken) "Taken" else if (dateStr == todayDate.toString()) "Overdue" else "Missed"
                                
                                val time = LocalTime.parse(timeStr)
                                val formattedTime = time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
                                val dateLabel = when (i) {
                                    0 -> "Today"
                                    1 -> "Yesterday"
                                    else -> scanDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE • MMM d", Locale.getDefault()))
                                }
                                val timestamp = scanDate.atTime(time).atZone(forcedZone).toInstant().toEpochMilli()
                                
                                val logKey = "${med.id}_${dateStr}_${timeStr}"
                                val matchingLog = logsMap[logKey]

                                val actualCompletionTime = if (matchingLog != null && matchingLog.status.startsWith("TAKEN")) {
                                    val localTime = java.time.Instant.ofEpochMilli(matchingLog.lastUpdatedAt)
                                        .atZone(forcedZone)
                                        .toLocalTime()
                                    localTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
                                } else null

                                val delayFromScheduled = if (matchingLog != null && matchingLog.status.startsWith("TAKEN")) {
                                    val delayMs = matchingLog.lastUpdatedAt - timestamp
                                    val totalMinutes = Math.abs(delayMs) / (60 * 1000)
                                    val hours = totalMinutes / 60
                                    val mins = totalMinutes % 60
                                    val durationStr = when {
                                        hours > 0 -> "${hours}h ${mins}m"
                                        else -> "${mins}m"
                                    }
                                    if (delayMs >= 60 * 1000) {
                                        "$durationStr late"
                                    } else if (delayMs <= -60 * 1000) {
                                        "$durationStr early"
                                    } else {
                                        "On time"
                                    }
                                } else null

                                val overdueDuration = if (status == "Overdue") {
                                    val nowMs = anchorTime.toInstant().toEpochMilli()
                                    val delayMs = nowMs - timestamp
                                    if (delayMs > 0) {
                                        val totalMinutes = delayMs / (60 * 1000)
                                        val hours = totalMinutes / 60
                                        val mins = totalMinutes % 60
                                        when {
                                            hours > 0 -> "${hours}h ${mins}m"
                                            else -> "${mins}m"
                                        }
                                    } else null
                                } else null

                                historyActivities.add(
                                    ActivityLog(
                                        id = "${med.id}_${dateStr}_${timeStr}",
                                        medicineId = med.id,
                                        medicineName = med.name,
                                        status = status,
                                        time = formattedTime,
                                        dateLabel = dateLabel,
                                        timestamp = timestamp,
                                        actualCompletionTime = actualCompletionTime,
                                        overdueDuration = overdueDuration,
                                        delayFromScheduled = delayFromScheduled,
                                        scheduledDate = dateStr
                                    )
                                )
                            }
                        }
                    }
                }
            }
            historyActivities.sortByDescending { it.timestamp }

            _uiState.value = DashboardUiState(
                todayProgress = todayProgress,
                globalStreak = globalStreak,
                weeklyAdherence = weeklyAdherence,
                totalTaken = totalTaken,
                totalMissed = totalMissed,
                lastUpdated = lastUpdated,
                isEmpty = false,
                motivationalMessage = message,
                dailyResults = report.dailyResults,
                monthlyTaken = monthlyTaken,
                monthlyTotal = monthlyTotal,
                longestStreak = longestStreak,
                insights = insightsList,
                riskAlert = resolvedAlert,
                todayTaken = report.todayStats.taken,
                todayTotal = report.todayStats.total,
                nextReminderTime = calculatedTime,
                nextReminderDay = calculatedDay,
                missedDoseRisk = riskLevel,
                missedDoseRiskExplanation = riskExplanation,
                adherenceStatus = calculatedAdherenceStatus,
                adherenceExplanation = calculatedAdherenceExplanation,
                refillStatus = calculatedRefillStatus,
                refillExplanation = calculatedRefillExplanation,
                recentMissedCount = recentMissedCount,
                recentActivities = recentActivities,
                monthlyOverdueCount = monthlyOverdueCount,
                monthlyBestStreak = monthlyBestStreak,
                monthOverMonthImprovement = monthOverMonthImprovement,
                eveningMissPercent = eveningMissPercent,
                thisWeekStats = thisWeekStats,
                lastWeekStats = lastWeekStats,
                last30DaysStats = last30DaysStats,
                historyActivities = historyActivities,
                medicines = medicines
            )
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Calculation error", e)
            _uiState.value = _uiState.value.copy(isEmpty = medicines.isEmpty(), lastUpdated = "Error")
        }
    }

    fun refillMedicine(medicineId: String, quantity: Int) {
        viewModelScope.launch {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            medRepo.refillMedicine(userId, medicineId, quantity)
        }
    }

}
