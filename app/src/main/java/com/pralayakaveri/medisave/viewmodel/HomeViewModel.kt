package com.pralayakaveri.medisave.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pralayakaveri.medisave.data.MedicineRepository
import com.pralayakaveri.medisave.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import com.pralayakaveri.medisave.util.*
import java.util.Locale
import java.util.Calendar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp

data class ScheduleItem(
    val medicine: Medicine,
    val time: String,
    val isTaken: Boolean,
    val status: DoseStatus,
    val isNextUp: Boolean = false,
    // Presentation-layer flag: scheduled time + grace has passed but MISSED not yet written
    // (e.g. device was off, in Doze, or offline when MissedDoseWorker was supposed to fire).
    // No DB write; purely drives which section header the item renders under.
    val isComputedOverdue: Boolean = false
)

enum class DayStatus {
    TAKEN, MISSED, PARTIAL, TODAY, FUTURE, EMPTY, BLUE
}

data class DayAdherence(
    val date: java.time.LocalDate,
    val taken: Int,
    val total: Int,
    val percentage: Int,
    val status: DayStatus
)

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val scheduleItems: List<ScheduleItem>,
        val takenCount: Int,
        val pendingCount: Int,
        val totalCount: Int,
        val weeklyAdherence: Int?,
        val weeklyTaken: Int,
        val weeklyTotal: Int,
        val todayTaken: Int,
        val todayTotal: Int,
        val dailyStats: List<DayAdherence>,
        val currentStreak: Int,
        val bestStreak: Int,
        val displayAdherence: String,
        val adherenceLabel: String,
        val todayAdherenceLabel: String,
        val daysWithData: Int,
        val isStartingToday: Boolean,
        val startsTomorrow: Boolean = false,
        val todayIndex: Int,
        val hasRefillAlert: Boolean,
        val lowStockMedicine: Medicine? = null,
        val adherenceTheme: com.pralayakaveri.medisave.model.AdherenceThemeConfig,
        val debugInfo: String = ""
    ) : HomeUiState()
    object Empty : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val medRepo = MedicineRepository(application)
    private val prefManager = com.pralayakaveri.medisave.data.PreferenceManager(application)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    val userName: StateFlow<String> = com.pralayakaveri.medisave.data.AppDatabase.getDatabase(application)
        .userDao()
        .getPrimaryUserFlow()
        .map { it?.name ?: "User" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "User")

    private val refreshTrigger = MutableStateFlow(System.currentTimeMillis())
    private var refreshJob: kotlinx.coroutines.Job? = null
    private var authJob: kotlinx.coroutines.Job? = null

    private fun getAuthenticatedUserFlow(): Flow<String?> = callbackFlow {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            trySend(uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    init {
        loadData()
    }

    private fun loadData() {
        // 1. Reactive Auth-Stabilized Firestore Sync & Reconciliation
        viewModelScope.launch {
            getAuthenticatedUserFlow().collect { userId ->
                // Cancel previous auth job and flows cleanly to invoke awaitClose
                authJob?.cancel()
                authJob = null

                if (userId != null) {
                    android.util.Log.d("HomeViewModel", "Auth stabilized for UID: $userId. Starting background sync and reconciliation.")
                    
                    authJob = viewModelScope.launch {
                        // Background Sync
                        launch {
                            medRepo.getMedicinesFlow(userId)
                                .catch { e ->
                                    android.util.Log.e("HomeViewModel", "Error in background medicines sync flow", e)
                                }
                                .collect()
                        }

                        // Reconciliation pass: converts stale PENDING → MISSED doses on app launch.
                        launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                reconcileStaleOverdueDoses(userId)
                            } catch (e: Exception) {
                                android.util.Log.e("HomeViewModel", "Error in stale doses reconciliation pass", e)
                            }
                        }
                    }
                } else {
                    android.util.Log.d("HomeViewModel", "User logged out. Background sync cancelled.")
                }
            }
        }

        // 2. Instant Local Room Collection (Zero Wait, Absolute Offline-First)
        viewModelScope.launch {
            combine(
                medRepo.getMedicinesFlowLocalByProfile("primary").map { "primary" to it },
                refreshTrigger
            ) { profileData, _ -> profileData }.collect { (profileId, rawList) ->
                if (rawList.isEmpty()) {
                    _uiState.value = HomeUiState.Empty
                    return@collect
                }

                val forcedZone = java.time.ZoneId.of("Asia/Kolkata")
                val anchorTime = java.time.ZonedDateTime.now(forcedZone)
                val todayDate = anchorTime.toLocalDate()
                val todayStr = todayDate.toString()

                // 1. CALCULATE CORE ADHERENCE
                val report = AdherenceCalculator.calculateReport(
                    medicines = rawList,
                    anchorTime = anchorTime
                )

                // 2. GENERATE SORTED SCHEDULE LIST
                val displayList = mutableListOf<ScheduleItem>()
                rawList.forEach { med: Medicine ->
                    med.times.forEach { time: String ->
                        if (ScheduleUtils.isDoseValid(med, todayStr, time)) {
                            val effectiveStatus = med.getStatusAsEnum(todayStr, time)
                            displayList.add(
                                ScheduleItem(
                                    medicine = med,
                                    time = time,
                                    isTaken = effectiveStatus == DoseStatus.TAKEN || 
                                             effectiveStatus == DoseStatus.TAKEN_ON_TIME || 
                                             effectiveStatus == DoseStatus.TAKEN_LATE || 
                                             effectiveStatus == DoseStatus.TAKEN_EARLY,
                                    status = effectiveStatus
                                )
                            )
                        }
                    }
                }

                // 3. COMPUTE OVERDUE FLAG PER ITEM (presentation-layer only — no DB writes)
                //    A dose is "computedOverdue" when:
                //      • it is not taken / skipped
                //      • its status is still PENDING in the DB (MissedDoseWorker hasn't fired)
                //      • scheduled time + grace period has already elapsed
                val systemZone = java.time.ZoneId.systemDefault()
                val nowZoned = java.time.ZonedDateTime.now(systemZone)

                val displayListWithFlags = displayList.map { item ->
                    val timeParts = item.time.split(":")
                    val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
                    val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                    val scheduledDateTime = nowZoned.toLocalDate().atTime(hour, minute).atZone(systemZone)
                    val graceCutoff = scheduledDateTime.plusMinutes(item.medicine.gracePeriodMinutes.toLong())
                    val isComputedOverdue = !item.isTaken
                        && item.status != DoseStatus.MISSED
                        && item.status != DoseStatus.SKIPPED_AUTO
                        && item.status != DoseStatus.SKIPPED_NO_STOCK
                        && scheduledDateTime.isBefore(nowZoned)
                        && graceCutoff.isBefore(nowZoned)
                    item.copy(isComputedOverdue = isComputedOverdue)
                }

                // 4. DETERMINE THE SINGLE CHRONOLOGICALLY NEXT UP PENDING DOSE (Timezone-Aware)
                //    Overdue doses are explicitly excluded from Next Up eligibility.
                val nextUpTarget = displayListWithFlags
                    .filter { it.status == DoseStatus.PENDING && it.medicine.pillsLeft > 0 && !it.isComputedOverdue }
                    .map { item ->
                        val timeParts = item.time.split(":")
                        val hour = timeParts[0].toInt()
                        val minute = timeParts[1].toInt()
                        val scheduledDateTime = nowZoned.toLocalDate()
                            .atTime(hour, minute)
                            .atZone(systemZone)
                        item to scheduledDateTime
                    }
                    .filter { (_, scheduledZonedDateTime) ->
                        scheduledZonedDateTime.isAfter(nowZoned)
                    }
                    .minByOrNull { (_, scheduledZonedDateTime) -> scheduledZonedDateTime }
                    ?.first

                val nextUpTargetKey = nextUpTarget?.let { "${it.medicine.id}_${it.time}" }

                // 5. SORT INTO THREE SECTIONS:
                //    Group 0 = Pending (future, not yet due)  → rendered first
                //    Group 1 = Overdue (grace expired, unresolved)  → rendered second
                //    Group 2 = Completed (taken / skipped / confirmed missed)  → rendered last
                val sortedList = displayListWithFlags.sortedWith(
                    compareBy(
                        {
                            when {
                                it.isTaken
                                    || it.status == DoseStatus.SKIPPED_AUTO
                                    || it.status == DoseStatus.SKIPPED_NO_STOCK -> 2
                                it.isComputedOverdue || it.status == DoseStatus.MISSED -> 1
                                else -> 0
                            }
                        },
                        { it.medicine.getTimeAsMinutes(it.time) },
                        { it.medicine.name }
                    )
                ).map { item ->
                    val compositeKey = "${item.medicine.id}_${item.time}"
                    item.copy(isNextUp = compositeKey == nextUpTargetKey)
                }

                // 3. STREAK CALCULATION (Unified Pipeline)
                val currentStreak = AdherenceCalculator.calculateStreak(rawList, todayDate)

                // 4. MAP TO UI STATE
                // 4. MAP TO UI THEME (Centralized Mapper)
                val adherenceTheme = AdherenceThemeMapper.mapReportToTheme(report, currentStreak)

                val dailyStats = report.dailyResults.map { day ->
                    DayAdherence(
                        date = day.date,
                        taken = day.taken,
                        total = day.total,
                        percentage = day.percentage,
                        status = when(day.status) {
                            AdherenceDayStatus.TAKEN -> DayStatus.TAKEN
                            AdherenceDayStatus.MISSED -> DayStatus.MISSED
                            AdherenceDayStatus.PARTIAL -> DayStatus.PARTIAL
                            AdherenceDayStatus.TODAY -> DayStatus.TODAY
                            AdherenceDayStatus.EMPTY -> DayStatus.EMPTY
                            AdherenceDayStatus.FUTURE -> DayStatus.FUTURE
                            AdherenceDayStatus.BLUE -> DayStatus.BLUE
                        }
                    )
                }

                val lowStock = rawList.firstOrNull { it.needsRefill() }

                // FINAL LOGGING
                if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
                    Log.d("DEBUG_ADHERENCE", "screen=Home | profileId=$profileId | meds=${rawList.size} | total=${report.todayStats.total} | taken=${report.todayStats.taken} | adherence=${report.adherencePercentage}%")
                }

                _uiState.value = HomeUiState.Success(
                    scheduleItems = sortedList,
                    takenCount = report.todayStats.taken, 
                    totalCount = report.todayStats.total,
                    // pendingCount: only future doses that are not overdue, not missed, not skipped.
                    // This is a UI counter only — AdherenceCalculator.todayStats.pending is unchanged.
                    pendingCount = sortedList.count {
                        !it.isTaken
                            && !it.isComputedOverdue
                            && it.status != DoseStatus.MISSED
                            && it.status != DoseStatus.SKIPPED_AUTO
                            && it.status != DoseStatus.SKIPPED_NO_STOCK
                    },
                    weeklyAdherence = report.adherencePercentage ?: 0,
                    weeklyTaken = report.weeklyStats.taken,
                    weeklyTotal = report.weeklyStats.total,
                    todayTaken = report.todayStats.taken, 
                    todayTotal = report.todayStats.total, 
                    dailyStats = dailyStats,
                    currentStreak = currentStreak,
                    bestStreak = currentStreak,
                    displayAdherence = report.displayPercentage,
                    adherenceLabel = "",
                    todayAdherenceLabel = "${report.todayStats.taken}/${report.todayStats.total} today",
                    daysWithData = report.daysWithData,
                    isStartingToday = false, 
                    todayIndex = dailyStats.indexOfFirst { it.date == todayDate }.coerceAtLeast(0),
                    hasRefillAlert = lowStock != null,
                    lowStockMedicine = lowStock,
                    adherenceTheme = adherenceTheme,
                    debugInfo = "Today: ${report.todayStats.taken}/${report.todayStats.total} | Weekly: ${report.adherencePercentage}% | Profile: $profileId"
                )
            }
        }
    }


    fun refresh() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    private fun scheduleNextAutoRefresh(medicines: List<Medicine>, anchorTime: java.time.ZonedDateTime) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // 1. Next Dose Time
            val nextDoseMs = medicines.map { it.calculateNextCheckAt(anchorTime) }
                .filter { it > 0 }
                .minOrNull() ?: Long.MAX_VALUE
            
            // 2. Next Midnight
            val nextMidnight = anchorTime.toLocalDate().plusDays(1).atStartOfDay(anchorTime.zone)
            val nextMidnightMs = nextMidnight.toInstant().toEpochMilli()
            
            val targetMs = minOf(nextDoseMs, nextMidnightMs)
            val delayMs = (targetMs - System.currentTimeMillis()).coerceAtLeast(1000L)
            
            if (delayMs < 24 * 3600 * 1000L) { // Only schedule if within 24h
                kotlinx.coroutines.delay(delayMs)
                refresh()
            }
        }
    }

    fun showDebugInfo(message: String) {
        viewModelScope.launch {
            _snackbarMessages.emit(message)
        }
    }

    fun markAsTaken(medId: String, time: String, taken: Boolean, status: DoseStatus? = null) {
        viewModelScope.launch {
            _isUpdating.value = true
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            // If taken is true, we use TAKEN. If false, we go back to PENDING.
            // We ignore the 'status' parameter for now as it's computed by the engine anyway.
            medRepo.updateMedicineStatus(userId, medId, null, time, if (taken) "TAKEN" else "PENDING")
            _isUpdating.value = false
        }
    }

    fun resetToday() {
        viewModelScope.launch {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val profileId = "primary"
            val todayStr = java.time.LocalDate.now().toString()
            
            val meds = medRepo.getMedicinesFlowLocalByProfile(profileId).first()
            meds.forEach { med ->
                med.times.forEach { time ->
                    medRepo.updateMedicineStatus(userId, med.id, null, time, "PENDING")
                }
            }
        }
    }

    fun refillStock(medicineId: String, medicineName: String, quantity: Int) {
        viewModelScope.launch {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            try {
                medRepo.refillMedicine(userId, medicineId, quantity).getOrThrow()
                _snackbarMessages.emit("Added $quantity pills to $medicineName ✓")
            } catch (e: Exception) {
                _snackbarMessages.emit("Refill failed: ${e.message}")
            }
        }
    }

    fun deleteMedicine(medicineId: String) {
        viewModelScope.launch {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            _isLoading.value = true
            try {
                medRepo.deleteMedicine(userId, medicineId).getOrThrow()
                _snackbarMessages.emit("Medicine deleted successfully")
            } catch (e: Exception) {
                _snackbarMessages.emit("Delete failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Reconciliation pass: converts stale PENDING doses to MISSED on app launch.
     *
     * When does this matter?
     *   • Device was powered off at alarm time  → alarm never fired, MissedDoseWorker never scheduled
     *   • Device in Doze mode                   → WorkManager deferred past grace window
     *   • Device was offline at Firestore sync   → Cloud Function wrote MISSED but it never arrived
     *
     * Safety guards (mirrors MissedDoseWorker.doWork()):
     *   1. Status must be PENDING in the local Room DB
     *   2. scheduledTime + gracePeriodMinutes must have elapsed
     *
     * Side effects:
     *   • Writes MISSED to Room (via MedicineRepository.updateMedicineStatus)
     *   • Queues Firestore sync (existing offline-safe pipeline)
     *   • Does NOT fire any notification — MissedDoseWorker owns that
     *   • Does NOT affect AlarmReceiver, caregiver alerts, or Cloud Functions
     */
    private suspend fun reconcileStaleOverdueDoses(userId: String) {
        try {
            val zone = java.time.ZoneId.of("Asia/Kolkata")
            val now = java.time.ZonedDateTime.now(zone)
            val todayStr = now.toLocalDate().toString()

            val medicines = medRepo.getMedicinesFlowLocalByProfile("primary").first()
            var reconciledCount = 0

            medicines.forEach { med ->
                med.times.forEach { timeStr ->
                    if (com.pralayakaveri.medisave.util.ScheduleUtils.isDoseValid(med, todayStr, timeStr)) {
                        val currentStatus = med.getStatusAt(todayStr, timeStr)
                        if (currentStatus == DoseStatus.PENDING.name) {
                            val parts = timeStr.split(":")
                            val hour = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                            val minute = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                            val scheduledTime = now.toLocalDate().atTime(hour, minute).atZone(zone)
                            val graceCutoff = scheduledTime.plusMinutes(med.gracePeriodMinutes.toLong())

                            if (graceCutoff.isBefore(now)) {
                                val minutesLate = java.time.Duration.between(graceCutoff, now).toMinutes()
                                android.util.Log.d(
                                    "HomeViewModel",
                                    "[Reconcile] Marking MISSED: ${med.name} @ $timeStr " +
                                    "(grace=${med.gracePeriodMinutes}m, overdue by ${minutesLate}m)"
                                )
                                medRepo.updateMedicineStatus(
                                    userId, med.id, todayStr, timeStr, DoseStatus.MISSED.name
                                )
                                reconciledCount++
                            }
                        }
                    }
                }
            }

            if (reconciledCount > 0) {
                android.util.Log.d("HomeViewModel", "[Reconcile] Completed: $reconciledCount dose(s) marked MISSED")
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "[Reconcile] Pass failed: ${e.message}")
        }
    }

    private fun getTodayWeekday(): Int {
        val calendar = Calendar.getInstance(Locale.getDefault())
        return DateUtils.mapToAppDay(calendar.get(Calendar.DAY_OF_WEEK))
    }
}
