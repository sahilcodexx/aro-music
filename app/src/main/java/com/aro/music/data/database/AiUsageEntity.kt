package com.aro.music.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// Legacy schema compatibility only; no active AI integration reads or writes this table.
@Entity(tableName = "ai_usage")
data class AiUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val provider: String,
    val model: String,
    val promptType: String,
    val promptTokens: Int,
    val outputTokens: Int,
    val thoughtTokens: Int
)
