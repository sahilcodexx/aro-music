package com.aro.music.spotify

import com.aro.music.data.database.SpotifyMatchDao
import com.aro.music.data.database.SpotifyMatchEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpotifyMatchDaoTest {
    private class Cache : SpotifyMatchDao {
        private val matches = mutableMapOf<String, SpotifyMatchEntity>()
        override suspend fun getSpotifyMatch(spotifyId: String) = matches[spotifyId]
        override suspend fun getByYouTubeId(youtubeId: String) = matches.values.find { it.youtubeId == youtubeId }
        override suspend fun insert(match: SpotifyMatchEntity) { matches[match.spotifyId] = match }
        override suspend fun delete(spotifyId: String) { matches.remove(spotifyId) }
    }

    @Test fun `automatic matcher cannot overwrite a user's selected recording`() = runTest {
        val cache = Cache()
        cache.upsertSpotifyMatch(SpotifyMatchEntity("track", "manual", "Song", "Artist", 1.0, isManualOverride = true))
        cache.upsertSpotifyMatch(SpotifyMatchEntity("track", "automatic", "Song", "Artist", 0.99))
        assertEquals("manual", cache.getSpotifyMatch("track")?.youtubeId)
        cache.upsertSpotifyMatch(SpotifyMatchEntity("track", "new-manual", "Song", "Artist", 1.0, isManualOverride = true))
        assertEquals("new-manual", cache.getSpotifyMatch("track")?.youtubeId)
    }
}
