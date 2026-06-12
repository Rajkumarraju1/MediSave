package com.pralayakaveri.medisave.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pralayakaveri.medisave.data.AuthRepository
import com.pralayakaveri.medisave.data.MedicineRepository
import com.pralayakaveri.medisave.model.Medicine
import com.pralayakaveri.medisave.reminder.ReminderManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val medRepo = MedicineRepository(application)
    private val reminderManager = ReminderManager(application)
    private val prefManager = com.pralayakaveri.medisave.data.PreferenceManager(application)

    // Form State
    var medicineName = mutableStateOf("")
    var quantity = mutableStateOf(1)
    var selectedForm = mutableStateOf("Tablet")
    val selectedTimes = mutableStateListOf<String>("08:00")
    val selectedDays = mutableStateListOf<Int>().apply { addAll(listOf(1, 2, 3, 4, 5, 6, 7)) }
    var instructions = mutableStateOf("")
    var errorMessage = mutableStateOf("")
    
    var pillTrackerEnabled = mutableStateOf(false)
    var pillsInHand = mutableStateOf("30")
    var alertThreshold = mutableStateOf("5")
    
    var pushNotificationEnabled = mutableStateOf(true)
    var caregiverAlertEnabled = mutableStateOf(true)
    var gracePeriodMinutes = mutableStateOf(30)
    var isStartTomorrow = mutableStateOf(false)

    private val _navigationEvent = MutableSharedFlow<Unit>()
    val navigationEvent: SharedFlow<Unit> = _navigationEvent

    fun addTime(time: String) {
        if (!selectedTimes.contains(time)) {
            selectedTimes.add(time)
            selectedTimes.sortBy { t -> t.split(":")[0].toInt() * 60 + t.split(":")[1].toInt() }
        }
    }

    fun removeTime(time: String) {
        if (selectedTimes.size > 1) {
            selectedTimes.remove(time)
        }
    }

    fun toggleDay(day: Int) {
        if (selectedDays.contains(day)) {
            selectedDays.remove(day)
        } else {
            selectedDays.add(day)
        }
    }

    fun loadMedicine(medicine: Medicine) {
        medicineName.value = medicine.name
        selectedForm.value = medicine.dose.split(" ").lastOrNull() ?: "Tablet"
        quantity.value = medicine.doseQuantity
        selectedTimes.clear()
        selectedTimes.addAll(medicine.times)
        selectedDays.clear()
        selectedDays.addAll(medicine.repeatDays)
        instructions.value = medicine.instruction
        pillTrackerEnabled.value = medicine.pillsLeft > 0
        pillsInHand.value = medicine.pillsLeft.toString()
        alertThreshold.value = medicine.refillAt.toString()
        gracePeriodMinutes.value = medicine.gracePeriodMinutes
        caregiverAlertEnabled.value = medicine.caregiverAlertEnabled
    }

    fun saveReminder() {
        errorMessage.value = ""
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        // Simple Validation
        if (medicineName.value.isBlank() || selectedTimes.isEmpty() || selectedDays.isEmpty()) {
            errorMessage.value = "Please fill all required fields"
            return
        }

        val finalPillsInHand = pillsInHand.value.toIntOrNull() ?: 0
        var finalQuantity = quantity.value.coerceIn(1, 3)
        
        val dailyDose = finalQuantity * selectedTimes.size
        
        if (pillTrackerEnabled.value && dailyDose > finalPillsInHand && finalPillsInHand > 0) {
            errorMessage.value = "Your daily dose exceeds available tablets"
            return
        }

        android.util.Log.d("RepeatDays", "Selected days (VM): ${selectedDays.toList()}")

        val sortedDays = selectedDays.sorted()
        
        val startDate = if (isStartTomorrow.value) {
            java.time.LocalDate.now().plusDays(1).toString()
        } else {
            java.time.LocalDate.now().toString()
        }

        val medicine = Medicine(
            name = medicineName.value,
            dose = "$finalQuantity ${selectedForm.value}",
            times = selectedTimes.toList(),
            instruction = instructions.value,
            statusMap = emptyMap(),
            pillsLeft = if (pillTrackerEnabled.value) finalPillsInHand else 0,
            totalStock = if (pillTrackerEnabled.value) finalPillsInHand else 0,
            isStockInferred = false,
            doseQuantity = finalQuantity,
            refillAt = if (pillTrackerEnabled.value) alertThreshold.value.toIntOrNull() ?: 5 else 5,
            repeatDays = sortedDays,
            startDate = startDate,
            gracePeriodMinutes = gracePeriodMinutes.value,
            caregiverAlertEnabled = caregiverAlertEnabled.value
        )

        viewModelScope.launch {
            try {
                val profileId = "primary"
                android.util.Log.d("ProfileAssociation", "Saving medicine with profileId: $profileId")
                
                val finalMedicine = medicine.copy(profileId = profileId)
                val medId = medRepo.addMedicine(userId, finalMedicine).getOrThrow()
                
                // Need to update the medicine object with its new Firestore ID for unique scheduling
                val medicineWithId = finalMedicine.copy(id = medId)
                reminderManager.scheduleAlarmsForMedicine(medicineWithId, userId, cancelTodayEscalations = true)
                
                _navigationEvent.emit(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
