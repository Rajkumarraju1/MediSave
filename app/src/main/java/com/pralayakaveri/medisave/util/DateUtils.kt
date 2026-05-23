package com.pralayakaveri.medisave.util

import java.util.Calendar

object DateUtils {
    /**
     * Maps Android Calendar days to App-specific day IDs.
     * Calendar.MONDAY (2) -> 1
     * ...
     * Calendar.SUNDAY (1) -> 7
     */
    fun mapToAppDay(calendarDay: Int): Int {
        return when (calendarDay) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }
}
