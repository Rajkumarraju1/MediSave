package com.pralayakaveri.medisave.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val brandName: String,
    val saltComposition: String,
    val normalizedSalt: String,
    val price: Double,
    val manufacturer: String,
    val strength: String = "",
    val packSize: Int? = null
)
