package com.example.aiattendancesystem.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromFloatArray(value: FloatArray): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toFloatArray(value: String): FloatArray {
        return if (value.isEmpty()) {
            FloatArray(0)
        } else {
            value.split(",").map { it.toFloat() }.toFloatArray()
        }
    }
}
