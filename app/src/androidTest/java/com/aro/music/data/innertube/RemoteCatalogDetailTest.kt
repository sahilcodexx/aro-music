package com.aro.music.data.innertube

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aro.music.data.service.tile.LastPlaylistTileService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises search → Room → detail using the app's real catalog/session. */
@RunWith(AndroidJUnit4::class)
class RemoteCatalogDetailTest {
    @Test fun ragaFullCatalogAndLibraryMembership() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, LastPlaylistTileService.LastPlaylistTileEntryPoint::class.java)
        val music = entry.musicRepository()
        val engine = InnerTubeEngine(context)
        val registry = InnerTubeBrowseRegistry(context)
        val repository = InnerTubeRepository(engine, registry)
        try {
            runBlocking {
                withTimeout(180_000) {
                    val before = music.getAudioFiles().first().map { it.id }.toSet()
                    val initialCount = music.getSongCountFlow().first()
                    val artist = repository.searchArtists("Raga").first { it.name.equals("Raga", true) }
                    val id = requireNotNull(registry.artistBrowseId(artist.id))
                    val page = repository.getArtistPage(id)
                    assertFalse("Artist must have subscribers", page.subscriberCount.isNullOrBlank())
                    assertFalse("Artist must have monthly audience", page.monthlyAudience.isNullOrBlank())
                    val required = listOf("Top songs", "Albums", "Singles & EPs", "Videos", "Featured on", "Fans might also like")
                    required.forEach { title -> assertTrue("Missing artist section: $title; received ${page.sections.map { it.title }}", page.sections.any { it.title.replace(" and ", " & ") == title && it.items.isNotEmpty() }) }
                    val singles = page.sections.first { it.title.replace(" and ", " & ") == "Singles & EPs" }
                    val expanded = repository.getCatalogSection(singles)
                    assertTrue("More singles should expand the catalog", expanded.items.size > singles.items.size)
                    val album = music.searchAlbums("no formula", 0).first().first { it.title.equals("NO FORMULA", true) }
                    val songs = music.getSongsForAlbum(album.id).first()
                    assertEquals("Browsing a catalog must not populate the library", before, music.getAudioFiles().first().map { it.id }.toSet())
                    val song = songs.first { it.id !in before }
                    try {
                        music.setFavoriteStatusWithSong(song, true)
                        val saved = music.getAudioFiles().first { rows -> rows.any { it.id == song.id } }
                        assertEquals(initialCount + 1, music.getSongCountFlow().first())
                        assertEquals(before + song.id, music.getSongIdsSorted(com.aro.music.data.model.SortOption.SongTitleAZ, com.aro.music.data.model.StorageFilter.ALL).map(Long::toString).toSet())
                        assertTrue("Saving a song must update library immediately", saved.any { it.id == song.id && it.isFavorite })
                        assertTrue("Online library queue includes saved streaming songs", music.getSongsPage(100, 0, com.aro.music.data.model.SortOption.SongTitleAZ, com.aro.music.data.model.StorageFilter.ONLINE).any { it.id == song.id })
                        assertFalse("Offline library excludes streaming songs", music.getSongsPage(100, 0, com.aro.music.data.model.SortOption.SongTitleAZ, com.aro.music.data.model.StorageFilter.OFFLINE).any { it.id == song.id })
                    } finally { music.setFavoriteStatusWithSong(song, false) }
                    val playlists = entry.playlistPreferencesRepository()
                    val testPlaylist = playlists.createPlaylist("Catalog membership test", listOf(song.id))
                    try {
                        assertTrue("Playlist songs belong to library", music.getAudioFiles().first().any { it.id == song.id })
                    } finally { playlists.deletePlaylist(testPlaylist.id) }
                    assertFalse("Unsaving removes the song from library but retains playback cache", music.getAudioFiles().first().any { it.id == song.id })
                    assertNotNull("Browse identities survive a new registry", InnerTubeBrowseRegistry(context).artistBrowseId(artist.id))
                }
            }
        } finally { engine.shutdown() }
    }

    @Test fun homeUsesListeningHistoryAndPins() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, LastPlaylistTileService.LastPlaylistTileEntryPoint::class.java)
        val engine = InnerTubeEngine(context)
        val repository = InnerTubeRepository(engine, InnerTubeBrowseRegistry(context))
        val memory = androidx.room.Room.inMemoryDatabaseBuilder(context, com.aro.music.data.database.AroDatabase::class.java).build()
        val files = java.io.File(context.cacheDir, "home-feed-test-history").apply { mkdirs() }
        val isolatedContext = object : android.content.ContextWrapper(context) {
            override fun getFilesDir(): java.io.File = files
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                super.getSharedPreferences("home_feed_test_$name", mode)
        }
        val preferenceNames = listOf("home_speed_dial", "home_feed_snapshot")
        preferenceNames.forEach { isolatedContext.getSharedPreferences(it, 0).edit().clear().commit() }
        val stats = com.aro.music.data.stats.PlaybackStatsRepository(isolatedContext)
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var model: com.aro.music.presentation.viewmodel.HomeFeedViewModel
        var pinned: com.aro.music.data.model.Song? = null
        try {
            runBlocking {
                withTimeout(180_000) {
                    val seed = repository.searchSongs("Bhagwaan Karma").first { it.title.equals("Bhagwaan", true) }
                    entry.musicRepository().cacheCatalogSongs(listOf(seed))
                    stats.recordPlayback(seed.id, 30_000)
                    instrumentation.runOnMainSync {
                        model = com.aro.music.presentation.viewmodel.HomeFeedViewModel(repository, memory.musicDao(), entry.musicRepository(), stats, isolatedContext)
                        store.put("home", model)
                        model.onSongPlayed(seed)
                    }
                    val state = model.state.first { feed -> !feed.isLoading && feed.sections.isNotEmpty() && feed.personalSections.any { it.title == "Recommended for you" } }
                    assertTrue("History appears after even one song", state.personalSections.any { it.title == "Recently played" && it.items.any { row -> row.id == "song:${seed.id}" } })
                    assertTrue("Speed dial includes recent listening", state.personalSections.any { it.title == "Speed dial" && it.items.any { row -> row.id == "song:${seed.id}" } })
                    assertTrue("Home follows the rap seed", state.personalSections.first { it.title == "Recommended for you" }.items.any { it.title == "WDTBU" || it.title == "Karta Kya Hai" })
                    assertEquals(listOf("Recently played", "Speed dial"), state.personalSections.take(2).map { it.title })
                    val recommendation = state.personalSections.first { it.title == "Recommended for you" }
                    val nextSong = (recommendation.items.first() as HomeCatalogItem.Track).song
                    instrumentation.runOnMainSync { model.onSongPlayed(nextSong) }
                    model.state.first { feed -> feed.personalSections.any { it.title == "Speed dial" && it.items.any { row -> row.id == "song:${nextSong.id}" } } }
                    assertEquals("Changing songs keeps the recommendation snapshot", recommendation,
                        model.state.value.personalSections.first { it.title == "Recommended for you" })
                    instrumentation.runOnMainSync {
                        store.clear()
                        model = com.aro.music.presentation.viewmodel.HomeFeedViewModel(repository, memory.musicDao(), entry.musicRepository(), stats, isolatedContext)
                        store.put("home", model)
                        model.onSongPlayed(nextSong)
                    }
                    val restored = model.state.first { feed -> feed.sections.isNotEmpty() &&
                        feed.personalSections.any { it.title == "Recommended for you" } &&
                        feed.personalSections.any { it.title == "Speed dial" && it.items.any { row -> row.id == "song:${nextSong.id}" } } }
                    assertEquals("A fresh snapshot survives app recreation", recommendation,
                        restored.personalSections.first { it.title == "Recommended for you" })
                    if (seed.id !in model.state.value.pinnedSongIds) {
                        pinned = seed
                        instrumentation.runOnMainSync { model.toggleSpeedDial(seed) }
                        assertTrue(seed.id in model.state.value.pinnedSongIds)
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { pinned?.let { model.toggleSpeedDial(it) }; store.clear() }
            memory.close()
            engine.shutdown()
            files.deleteRecursively()
            preferenceNames.forEach { isolatedContext.getSharedPreferences(it, 0).edit().clear().commit() }
        }
    }

    @Test fun noFormulaAndRagaOpenWithSongs() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val repository = EntryPointAccessors.fromApplication(context, LastPlaylistTileService.LastPlaylistTileEntryPoint::class.java).musicRepository()
        runBlocking {
            withTimeout(180_000) {
                val albums = repository.searchAlbums("no formula", 0).first()
                val album = requireNotNull(albums.firstOrNull { it.title.equals("NO FORMULA", true) }) { "NO FORMULA missing from search" }
                assertEquals("Search album header must exist before opening", album.title, repository.getAlbumById(album.id).first()?.title)
                val albumSongs = repository.getSongsForAlbum(album.id).first()
                assertTrue("NO FORMULA must have playable tracks", albumSongs.isNotEmpty())
                assertTrue("Album rows must retain the selected parent", albumSongs.all { it.albumId == album.id })
                val artists = repository.searchArtists("raga").first()
                val artist = requireNotNull(artists.firstOrNull { it.name.equals("Raga", true) }) { "Raga missing from search" }
                val artistSongs = repository.getSongsForArtist(artist.id).first()
                assertTrue("Raga must have playable tracks through artist relations", artistSongs.isNotEmpty())
                assertEquals("Track bylines must not rename the artist", artist.name, repository.getArtistById(artist.id).first()?.name)
            }
        }
    }
}
