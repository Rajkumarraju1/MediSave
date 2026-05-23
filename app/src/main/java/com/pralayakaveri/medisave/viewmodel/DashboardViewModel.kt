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

data class DashboardUiState(
    val todayProgress: Float = 0f,
    val globalStreak: Int = 0,
    val weeklyAdherence: Int = 0,
    val totalTaken: Int = 0,
    val totalMissed: Int = 0,
    val lastUpdated: String = "",
    val isEmpty: Boolean = true,
    val motivationalMessage: String = "Let's start your health journey!"
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
            
            // USE CENTRALIZED CALCULATOR
            val report = AdherenceCalculator.calculateReport(
                medicines = medicines,
                anchorTime = anchorTime
            )

            // 1. Today Progress
            val todayProgress = if (report.todayStats.total > 0) 
                report.todayStats.taken.toFloat() / report.todayStats.total 
            else 0f

            // 2. Global Streak
            val todayDate = anchorTime.toLocalDate()
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

            _uiState.value = DashboardUiState(
                todayProgress = todayProgress,
                globalStreak = globalStreak,
                weeklyAdherence = weeklyAdherence,
                totalTaken = totalTaken,
                totalMissed = totalMissed,
                lastUpdated = lastUpdated,
                isEmpty = false,
                motivationalMessage = message
            )
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Calculation error", e)
            _uiState.value = _uiState.value.copy(isEmpty = medicines.isEmpty(), lastUpdated = "Error")
        }
    }

}
