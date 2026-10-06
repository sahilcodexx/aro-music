package com.aro.music.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SpotifyMatchDao {
    @Query("SELECT * FROM spotify_match WHERE spotifyId = :spotifyId")
    suspend fun getSpotifyMatch(spotifyId: String): SpotifyMatchEntity?

    @Query("SELECT * FROM spotify_match WHERE youtubeId = :youtubeId ORDER BY isManualOverride DESC, matchScore DESC LIMIT 1")
    suspend fun getByYouTubeId(youtubeId: String): SpotifyMatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(match: SpotifyMatchEntity)

    @Transaction
    suspend fun upsertSpotifyMatch(match: SpotifyMatchEntity) {
        if (match.isManualOverride || getSpotifyMatch(match.spotifyId)?.isManualOverride != true) insert(match)
    }

    @Query("DELETE FROM spotify_match WHERE spotifyId = :spotifyId")
    suspend fun delete(spotifyId: String)
}
