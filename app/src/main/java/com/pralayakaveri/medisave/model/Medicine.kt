package com.pralayakaveri.medisave.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.time.ZoneId

@IgnoreExtraProperties
data class Medicine(
    var id: String = "",
    val name: String = "",
    val dose: String = "",
    val times: List<String> = listOf("08:00"), // List of "HH:mm"
    val instruction: String = "",
    val statusMap: Map<String, String> = emptyMap(), // Key: "yyyy-MM-dd_HH:mm", Value: status
    val totalTaken: Int = 0,
    val totalMissed: Int = 0,
    val totalScheduled: Int = 0,
    val pillsLeft: Int = 0,
    val totalStock: Int = 0,
    val isStockInferred: Boolean = false,
    val lastUpdated: Timestamp? = null,
    val syncPending: Boolean = false,
    val doseQuantity: Int = 1,
    val refillAt: Int = 5,
    val colorHex: String = "#1D9E75",
    val repeatDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=Mon, 7=Sun
    val history: Map<String, List<String>> = emptyMap(),
    var profileId: String = "primary",
    val createdAt: Timestamp? = null,
    val startDate: String = "", // Format: "yyyy-MM-dd"
    val timezone: String = "Asia/Kolkata",
    val gracePeriodMinutes: Int = 10,
    val caregiverAlertEnabled: Boolean = true,
    val lastRefillNotifiedAt: Long = 0L,
    val nextCheckAt: Long = 0L, // Timestamp (ms) for the next scheduled server-side check
    val notifiedMap: Map<String, Long> = emptyMap() // Key: statusKey, Value: timestamp of notification
) {
    // Required for Firestore deserialization
    constructor() : this("")

    @Exclude
    fun getEffectiveZoneId(): java.time.ZoneId {
        // FORCE Asia/Kolkata everywhere as per requirement
        return ZoneId.of("Asia/Kolkata")
    }

    fun needsRefill(): Boolean {
        return pillsLeft <= refillAt
    }

    @Exclude
    fun constructStatusKey(dateStr: String, timeStr: String): String {
        return "${dateStr}_${timeStr}"
    }

    @Exclude
    fun getTimeAsMinutes(timeStr: String): Int {
        return try {
            val parts = timeStr.split(":")
            val hours = parts[0].toInt()
            val minutes = parts[1].toInt()
            hours * 60 + minutes
        } catch (e: Exception) {
            0
        }
    }

    @Exclude
    fun getStatusAt(dateStr: String, timeStr: String): String {
        return statusMap[constructStatusKey(dateStr, timeStr)] ?: DoseStatus.PENDING.name
    }

    @Exclude
    fun getStatusAsEnum(dateStr: String, timeStr: String): DoseStatus {
        return DoseStatus.fromString(getStatusAt(dateStr, timeStr))
    }

    @Exclude
    fun calculateNextCheckAt(anchorTime: java.time.ZonedDateTime): Long {
        val today = anchorTime.toLocalDate()
        
        // Check today and tomorrow
        for (i in 0..1) {
            val date = today.plusDays(i.toLong())
            val dateStr = date.toString()
            val dayOfWeek = date.dayOfWeek.value
            
            if (repeatDays.contains(dayOfWeek)) {
                times.forEach { timeStr ->
                    val status = getStatusAt(dateStr, timeStr)
                    if (status == DoseStatus.PENDING.name) {
                        val doseTime = java.time.LocalTime.parse(timeStr)
                        val checkTime = date.atTime(doseTime).atZone(anchorTime.zone).plusMinutes(30)
                        
                        // If this check time is in the future, it's our next target
                        if (checkTime.isAfter(anchorTime)) {
                            return checkTime.toInstant().toEpochMilli()
                        }
                    }
                }
            }
        }
        return 0L // No pending doses found in immediate future
    }
}
