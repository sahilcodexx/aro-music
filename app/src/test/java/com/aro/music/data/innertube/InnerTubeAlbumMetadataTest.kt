package com.aro.music.data.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InnerTubeAlbumMetadataTest {
    private fun track(subtitle: String) = InnerTubeSearchParser.parse(Json.parseToJsonElement("""
        {"contents":[{"musicTwoRowItemRenderer":{
          "navigationEndpoint":{"watchEndpoint":{"videoId":"test-song"}},
          "title":{"runs":[{"text":"Barsaat"}]},
          "subtitle":{"runs":[$subtitle]}
        }}]}
    """).jsonObject).tracks.single()

    @Test fun `play counts are not album titles`() {
        val song = track("""{"text":"Banjaare"},{"text":" • "},{"text":"22 crore plays"}""")
        assertEquals("Banjaare", song.artist)
        assertNull(song.album)
        assertNull(song.albumId)
    }

    @Test fun `missing album stays missing even when song and artist are known`() {
        val song = track("""{"text":"Aditya Rikhari"},{"text":" • "},{"text":"3:12"}""")
        assertEquals("Barsaat", song.title)
        assertEquals("Aditya Rikhari", song.artist)
        assertNull(song.album)
    }

    @Test fun `album name comes from its own link instead of subtitle position`() {
        val song = track("""
          {"text":"Banjaare"},{"text":" • "},{"text":"22 crore plays"},{"text":" • "},
          {"text":"Monsoon","navigationEndpoint":{"browseEndpoint":{"browseId":"MPRE-release"}}}
        """)
        assertEquals("Monsoon", song.album)
        assertEquals("MPRE-release", song.albumId)
    }

    @Test fun `a numeric real album name is retained`() {
        val song = track("""
          {"text":"Artist"},{"text":" • "},
          {"text":"1989","navigationEndpoint":{"browseEndpoint":{"browseId":"MPRE-1989"}}}
        """)
        assertEquals("1989", song.album)
    }
}
