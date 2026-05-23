package com.pralayakaveri.medisave.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dose_logs")
data class DoseLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val medicineId: String,
    val medicineName: String,
    val date: String,
    val time: String,
    val status: String,
    val lastUpdatedAt: Long,
    val notified: Boolean = false,
    val syncPending: Boolean = true,
    val pillCount: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "1") val caregiverAlertEnabled: Boolean = true
)
