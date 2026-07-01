package com.example.aiattendancesystem.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personId: Long,
    val personName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PRESENT"
)
