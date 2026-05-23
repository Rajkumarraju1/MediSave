package com.pralayakaveri.medisave.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromIntList(value: List<Int>?): String? {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int>? {
        val listType = object : TypeToken<List<Int>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromStatusMap(value: Map<String, String>?): String? {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStatusMap(value: String?): Map<String, String>? {
        if (value == null || value.isEmpty()) return emptyMap()
        return try {
            val mapType = object : TypeToken<Map<String, String>>() {}.type
            Gson().fromJson(value, mapType) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @TypeConverter
    fun fromHistoryMap(value: Map<String, List<String>>?): String? {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toHistoryMap(value: String?): Map<String, List<String>>? {
        val mapType = object : TypeToken<Map<String, List<String>>>() {}.type
        return Gson().fromJson(value, mapType)
    }

    @TypeConverter
    fun fromNotifiedMap(value: Map<String, Long>?): String? {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toNotifiedMap(value: String?): Map<String, Long>? {
        if (value == null || value.isEmpty()) return emptyMap()
        return try {
            val mapType = object : TypeToken<Map<String, Long>>() {}.type
            Gson().fromJson(value, mapType) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
