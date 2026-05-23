package com.pralayakaveri.medisave.util

fun formatTime(time: String): String {
    return try {
        val parts = time.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1]

        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }

        "$hour12:$minute $amPm"
    } catch (e: Exception) {
        time
    }
}

fun getTimestamp(dateStr: String, timeStr: String): Long {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val date = sdf.parse("$dateStr $timeStr")
        date?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}
