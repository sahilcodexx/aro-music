package com.aro.music.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// Legacy schema compatibility only; no active AI integration reads or writes this table.
@Dao
interface AiCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: AiCacheEntity)

    @Query("SELECT * FROM ai_cache WHERE promptHash = :hash")
    suspend fun getCache(hash: String): AiCacheEntity?

    @Query("DELETE FROM ai_cache WHERE timestamp < :olderThanTimestamp")
    suspend fun clearOldCache(olderThanTimestamp: Long)

    @Query("DELETE FROM ai_cache")
    suspend fun clearAllCache()
}
