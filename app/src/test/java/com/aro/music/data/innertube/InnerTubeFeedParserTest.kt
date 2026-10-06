package com.aro.music.data.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InnerTubeFeedParserTest {
    private fun fixture(name: String) = Json.parseToJsonElement(requireNotNull(javaClass.getResource("/innertube/$name")).readText()).jsonObject

    @Test fun `anonymous home shelves expose real playlist sources`() {
        val home = fixture("home-playlist-shelves.json")
        assertTrue(InnerTubeSearchParser.parse(home).tracks.isEmpty())
        val playlists = InnerTubeSearchParser.parsePlaylists(home)
        assertTrue(playlists.isNotEmpty())
        assertTrue(playlists.all { it.browseId.startsWith("VL") })
    }

    @Test fun `radio parses title artist duration and continuation from queue panel`() {
        val page = InnerTubeSearchParser.parse(fixture("radio-panel.json"))
        assertEquals("Bhagwan", page.tracks.first().title)
        assertEquals("Karma Sutra", page.tracks.first().artist)
        assertTrue(page.tracks.first().durationSeconds!! > 0)
        assertTrue(page.tracks.size > 20)
        assertNotNull(page.continuation)
    }

    @Test fun `song with album and artist navigation is still a playable home item`() {
        val root = Json.parseToJsonElement("""{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Bhagwaan"}]},"navigationEndpoint":{"watchEndpoint":{"videoId":"seed"}},"subtitle":{"runs":[{"text":"KARMA","navigationEndpoint":{"browseEndpoint":{"browseId":"UCkarma","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}}]}}}""").jsonObject
        val track = InnerTubeSearchParser.parse(root).tracks.single()
        assertEquals("seed", track.videoId)
        assertEquals("UCkarma", track.artistId)
    }
    @Test fun `Bhagwaan radio follows the rap seed rather than matching search words`() {
        val page = InnerTubeSearchParser.parse(fixture("karma-radio.json"))
        assertEquals("Bhagwaan", page.tracks.first().title)
        assertEquals("DX15AR5-E4A", page.tracks.first().videoId)
        assertEquals(206, page.tracks.first().durationSeconds)
        assertTrue(page.tracks.any { it.title == "WDTBU" })
        assertTrue(page.tracks.any { it.title == "Karta Kya Hai" })
        assertFalse(page.tracks.any { it.artist == "Karma Sutra" })
    }

    @Test fun `song search duration is read from the subtitle when fixed columns are absent`() {
        val root = Json.parseToJsonElement("""{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"seed"},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Bhagwaan"}]}}},{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"KARMA"},{"text":" • "},{"text":"3:26"}]}}}]}}""").jsonObject
        assertEquals(206, InnerTubeSearchParser.parse(root).tracks.single().durationSeconds)
    }

    @Test fun `radio cover images are read from the flat thumbnail structure`() {
        val tracks = InnerTubeSearchParser.parse(fixture("karma-radio.json")).tracks
        assertTrue(tracks.all { !it.artworkUrl.isNullOrBlank() })
        assertTrue(tracks.first().artworkUrl!!.contains("=w1080-h1080"))
    }

    @Test fun `home shelves retain playlist identity titles and carousel artwork`() {
        val sections = InnerTubeSearchParser.parseHomeSections(fixture("home-playlist-shelves.json"))
        assertEquals(2, sections.size)
        assertEquals("Dancing on your own", sections.first().title)
        assertTrue(sections.all { it.playlists.isNotEmpty() })
        assertTrue(sections.flatMap { it.playlists }.all { !it.artworkUrl.isNullOrBlank() })
        assertTrue(sections.all { it.songs.isEmpty() })
    }

    @Test fun `explore exposes albums and music shelves without mistaking artist bylines for artists`() {
        val sections = InnerTubeSearchParser.parseHomeSections(fixture("explore-shelves.json"))
        val albums = sections.first { it.title == "New albums & singles" }.albums
        assertTrue(albums.isNotEmpty())
        assertTrue(albums.all { it.browseId.startsWith("MPRE") && !it.artworkUrl.isNullOrBlank() })
        assertFalse(albums.first().artist in listOf("EP", "Album", "Single"))
        assertTrue(sections.first { it.title == "Trending" }.songs.isNotEmpty())
        assertTrue(sections.all { it.artists.isEmpty() })
    }

    @Test fun `largest thumbnail is selected by dimensions rather than response ordering`() {
        val root = Json.parseToJsonElement("""{"playlistPanelVideoRenderer":{"videoId":"seed","title":{"runs":[{"text":"Track"}]},"thumbnail":{"thumbnails":[{"url":"https://example.com/large.jpg","width":800,"height":800},{"url":"https://example.com/small.jpg","width":60,"height":60}]}}}""").jsonObject
        assertEquals("https://example.com/large.jpg", InnerTubeSearchParser.parse(root).tracks.single().artworkUrl)
    }

}
