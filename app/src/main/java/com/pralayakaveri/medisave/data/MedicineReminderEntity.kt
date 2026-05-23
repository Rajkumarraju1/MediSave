package com.pralayakaveri.medisave.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pralayakaveri.medisave.model.Medicine

@Entity(tableName = "medicine_reminders")
data class MedicineReminderEntity(
    @PrimaryKey val id: String, // Corresponding to Firestore medicine.id
    val name: String,
    val dose: String,
    val times: List<String>,
    val instruction: String,
    val statusMap: Map<String, String> = emptyMap(),
    val totalTaken: Int = 0,
    val totalMissed: Int = 0,
    val totalScheduled: Int = 0,
    val pillsLeft: Int,
    val totalStock: Int,
    val isStockInferred: Boolean,
    val lastUpdated: Long,
    val syncPending: Boolean,
    val doseQuantity: Int,
    val refillAt: Int,
    val colorHex: String,
    val repeatDays: List<Int>,
    val history: Map<String, List<String>> = emptyMap(),
    val profileId: String = "primary",
    val createdAt: Long = 0L,
    val timezone: String,
    val startDate: String = "",
    @androidx.room.ColumnInfo(defaultValue = "10") val gracePeriodMinutes: Int = 10,
    @androidx.room.ColumnInfo(defaultValue = "1") val caregiverAlertEnabled: Boolean = true,
    @androidx.room.ColumnInfo(defaultValue = "0") val lastRefillNotifiedAt: Long = 0L,
    @androidx.room.ColumnInfo(defaultValue = "0") val nextCheckAt: Long = 0L,
    val notifiedMap: Map<String, Long> = emptyMap()
) {
    fun toMedicine(): Medicine = Medicine(
        id = id,
        name = name,
        dose = dose,
        times = times,
        instruction = instruction,
        statusMap = statusMap,
        totalTaken = totalTaken,
        totalMissed = totalMissed,
        totalScheduled = totalScheduled,
        pillsLeft = pillsLeft,
        totalStock = totalStock,
        isStockInferred = isStockInferred,
        lastUpdated = if (lastUpdated > 0) com.google.firebase.Timestamp(lastUpdated / 1000, ((lastUpdated % 1000) * 1_000_000).toInt()) else null,
        syncPending = syncPending,
        doseQuantity = doseQuantity,
        refillAt = refillAt,
        caregiverAlertEnabled = caregiverAlertEnabled,
        colorHex = colorHex,
        repeatDays = repeatDays,
        history = history,
        profileId = profileId,
        createdAt = if (createdAt > 0) com.google.firebase.Timestamp(createdAt / 1000, ((createdAt % 1000) * 1_000_000).toInt()) else null,
        timezone = timezone,
        startDate = startDate,
        gracePeriodMinutes = gracePeriodMinutes,
        lastRefillNotifiedAt = lastRefillNotifiedAt,
        nextCheckAt = nextCheckAt,
        notifiedMap = notifiedMap
    )

    fun getStatusAt(dateStr: String, timeStr: String): String {
        return statusMap["${dateStr}_${timeStr}"] ?: com.pralayakaveri.medisave.model.DoseStatus.PENDING.name
    }

    companion object {
        fun fromMedicine(medicine: Medicine): MedicineReminderEntity = MedicineReminderEntity(
            id = medicine.id,
            name = medicine.name,
            dose = medicine.dose,
            times = medicine.times,
            instruction = medicine.instruction,
            statusMap = medicine.statusMap,
            totalTaken = medicine.totalTaken,
            totalMissed = medicine.totalMissed,
            totalScheduled = medicine.totalScheduled,
            pillsLeft = medicine.pillsLeft,
            totalStock = medicine.totalStock,
            isStockInferred = medicine.isStockInferred,
            lastUpdated = medicine.lastUpdated?.toDate()?.time ?: 0L,
            syncPending = medicine.syncPending,
            doseQuantity = medicine.doseQuantity,
            refillAt = medicine.refillAt,
            colorHex = medicine.colorHex,
            repeatDays = medicine.repeatDays,
            history = medicine.history,
            profileId = medicine.profileId,
            createdAt = medicine.createdAt?.toDate()?.time ?: 0L,
            timezone = medicine.timezone,
            startDate = medicine.startDate,
            gracePeriodMinutes = medicine.gracePeriodMinutes,
            caregiverAlertEnabled = medicine.caregiverAlertEnabled,
            lastRefillNotifiedAt = medicine.lastRefillNotifiedAt,
            nextCheckAt = medicine.nextCheckAt,
            notifiedMap = medicine.notifiedMap
        )
    }
}
