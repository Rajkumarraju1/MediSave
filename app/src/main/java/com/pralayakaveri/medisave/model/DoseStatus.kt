package com.pralayakaveri.medisave.model

enum class DoseStatus {
    PENDING,
    TAKEN,           // Legacy/Fallback
    TAKEN_ON_TIME,
    TAKEN_LATE,
    TAKEN_EARLY,
    MISSED,
    SKIPPED_NO_STOCK,
    SKIPPED_AUTO;

    companion object {
        fun fromString(value: String?): DoseStatus {
            return try {
                valueOf(value?.uppercase() ?: "PENDING")
            } catch (e: Exception) {
                PENDING
            }
        }
    }
}
