package com.example.aiattendancesystem.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

@Dao
interface AttendanceDao {

    // ── Person operations ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person): Long

    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun getAllPersons(): LiveData<List<Person>>

    @Query("SELECT * FROM persons")
    suspend fun getAllPersonsList(): List<Person>

    @Query("SELECT COUNT(*) FROM persons")
    fun getPersonCount(): LiveData<Int>

    @Delete
    suspend fun deletePerson(person: Person)

    // ── Attendance operations ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceRecord): Long

    @Query("SELECT * FROM attendance_records WHERE timestamp >= :startOfDay AND timestamp < :endOfDay ORDER BY timestamp DESC")
    fun getAttendanceForDay(startOfDay: Long, endOfDay: Long): LiveData<List<AttendanceRecord>>

    @Query("SELECT COUNT(*) FROM attendance_records WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    fun getTodayAttendanceCount(startOfDay: Long, endOfDay: Long): LiveData<Int>

    @Query("SELECT COUNT(*) FROM attendance_records WHERE personId = :personId AND timestamp >= :startOfDay AND timestamp < :endOfDay")
    suspend fun isAlreadyMarkedToday(personId: Long, startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT * FROM attendance_records ORDER BY timestamp DESC")
    fun getAllAttendance(): LiveData<List<AttendanceRecord>>

    @Delete
    suspend fun deleteAttendance(record: AttendanceRecord)

    // For PDF generation — returns a plain list, not LiveData
    @Query("SELECT * FROM attendance_records WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp ASC")
    suspend fun getAttendanceForDayList(start: Long, end: Long): List<AttendanceRecord>
}
