package com.example.aiattendancesystem.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val faceEmbedding: FloatArray,
    val registeredAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Person
        if (id != other.id) return false
        if (name != other.name) return false
        if (!faceEmbedding.contentEquals(other.faceEmbedding)) return false
        if (registeredAt != other.registeredAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + faceEmbedding.contentHashCode()
        result = 31 * result + registeredAt.hashCode()
        return result
    }
}
