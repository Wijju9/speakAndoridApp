package com.example.speakandroid

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val createdAt: Long,
    val durationMillis: Long,
    val englishText: String,
    val hindiText: String,
    val gujaratiText: String
)
