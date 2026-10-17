package com.aro.music.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// Legacy schema compatibility only; no active AI integration reads or writes this table.
@Entity(tableName = "ai_cache")
data class AiCacheEntity(
    @PrimaryKey
    val promptHash: String, // SHA-256 hash
    val responseJson: String,
    val timestamp: Long
)
