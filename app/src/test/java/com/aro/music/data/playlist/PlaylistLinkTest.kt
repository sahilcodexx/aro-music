package com.aro.music.data.playlist

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlaylistLinkTest {
    @Test fun `youtube watch music and shared playlist links resolve the same identity`() {
        val id = "PLabcdefghijklmnopqrstuv"
        listOf("https://www.youtube.com/playlist?list=$id", "https://music.youtube.com/playlist?list=$id&si=shared",
            "https://youtu.be/video?list=$id", "youtube.com/watch?v=video&list=$id", "https://M.YOUTUBE.COM/playlist?list=$id#end").forEach {
            assertEquals(PlaylistLink(PlaylistLink.Provider.YOUTUBE, id), PlaylistLink.parse(it))
        }
    }
    @Test fun `spotify shared international and app links resolve the same identity`() {
        val id = "37i9dQZF1DXcBWIGoYBM5M"
        listOf("https://open.spotify.com/playlist/$id?si=test", "https://open.spotify.com/intl-en/playlist/$id", "spotify:playlist:$id").forEach {
            assertEquals(PlaylistLink(PlaylistLink.Provider.SPOTIFY, id), PlaylistLink.parse(it))
        }
    }
    @Test fun `invalid and misleading links do not trigger arbitrary requests`() {
        listOf("", "https://example.com/playlist?list=PLabcdefghijklmnop", "https://youtube.com.evil.test/playlist?list=PLabcdefghijklmnop",
            "https://youtube.com@evil.test/playlist?list=PLabcdefghijklmnop", "https://youtube.com/watch?v=abcdefghijk",
            "https://open.spotify.com/track/37i9dQZF1DXcBWIGoYBM5M", "spotify:playlist:bad", "https://music.youtube.com/playlist?list=../../file").forEach {
            assertNull(PlaylistLink.parse(it), it)
        }
    }
}
