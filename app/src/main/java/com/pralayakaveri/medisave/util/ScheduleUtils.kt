package com.pralayakaveri.medisave.util

import com.pralayakaveri.medisave.model.Medicine
import com.pralayakaveri.medisave.model.DoseStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Single Source of Truth for Medicine Scheduling and Validation.
 * Use this to determine if a medicine should be visible or counted on a specific day.
 */
object ScheduleUtils {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Determines if a medicine is scheduled for a specific date based on:
     * 1. Start Date (cannot be before)
     * 2. Repeat Days (must match weekday)
     * 3. Logs (overrides scheduling - if it was taken, it's valid regardless of repeat days)
     */
    fun isScheduledForDate(medicine: Medicine, date: LocalDate): Boolean {
        // 1. Check Repeat Days
        val weekday = date.dayOfWeek.value // 1 (Mon) to 7 (Sun)
        val isRepeatDay = medicine.repeatDays.contains(weekday)
        
        // 2. Check Start Date
        val startDate = try { 
            if (medicine.startDate.isNotEmpty()) LocalDate.parse(medicine.startDate) 
            else LocalDate.MIN 
        } catch (e: Exception) { 
            LocalDate.MIN 
        }
        val isStarted = !date.isBefore(startDate)
        
        // 3. Check for any logs on this date (if it has a log, it was "scheduled" or at least "active")
        val dateStr = date.toString()
        val hasLogForDate = medicine.statusMap.keys.any { it.startsWith(dateStr) }
        
        return (isRepeatDay && isStarted) || hasLogForDate
    }

    /**
     * Determines if a specific dose slot is valid.
     */
    fun isDoseValid(medicine: Medicine, dateStr: String, timeStr: String): Boolean {
        return try {
            val date = LocalDate.parse(dateStr)
            val startDate = try { 
                if (medicine.startDate.isNotEmpty()) LocalDate.parse(medicine.startDate) 
                else LocalDate.MIN 
            } catch (e: Exception) { 
                LocalDate.MIN 
            }
            
            val isRepeatDay = medicine.repeatDays.contains(date.dayOfWeek.value)
            val isStarted = !date.isBefore(startDate)
            val hasLog = medicine.statusMap.containsKey("${dateStr}_$timeStr")
            
            val isValid = (isRepeatDay && isStarted) || hasLog
            
            android.util.Log.d("DOSE_DEBUG", "Med: ${medicine.name} | Date: $dateStr | Time: $timeStr | Start: ${medicine.startDate} | IsRepeat: $isRepeatDay | IsStarted: $isStarted | HasLog: $hasLog | Result: $isValid")
            
            isValid
        } catch (e: Exception) {
            true // Safety fallback
        }
    }

    /**
     * Filter a list of medicines for a specific date.
     */
    fun getMedicinesForDate(medicines: List<Medicine>, date: LocalDate): List<Medicine> {
        return medicines.filter { isScheduledForDate(it, date) }
    }
}
