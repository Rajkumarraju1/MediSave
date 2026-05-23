package com.pralayakaveri.medisave.model

import com.google.firebase.Timestamp

data class DoseLog(
    val id: String = "",
    val userId: String = "",
    val medicineName: String = "",
    val date: String = "",
    val time: String = "",
    val status: String = "PENDING",
    val lastUpdatedAt: Timestamp = Timestamp.now(),
    val notified: Boolean = false
)
