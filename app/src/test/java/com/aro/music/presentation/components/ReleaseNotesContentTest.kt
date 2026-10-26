package com.aro.music.presentation.components

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReleaseNotesContentTest {
    @Test fun `release tables preserve cells and become structured rows`() {
        val notes = "## Download\n\n| APK | Choose for | Size |\n| --- | :--- | ---: |\n| [Aro.apk](https://example.org/a.apk) | Most phones | 10 MiB |\n\n- **Fixed** playlists"
        val blocks = parseReleaseNotes(notes)
        assertEquals(ReleaseNoteBlock.Heading("Download", 2), blocks[0])
        val table = blocks[1] as ReleaseNoteBlock.Table
        assertEquals(listOf("APK", "Choose for", "Size"), table.headers)
        assertEquals(3, table.rows.single().size)
        assertEquals("10 MiB", table.rows.single().last())
        assertEquals(ReleaseNoteBlock.Bullet("**Fixed** playlists"), blocks.last())
    }
    @Test fun `inline formatting hides markup and rejects unsafe link schemes`() {
        assertEquals("Fixed playlists and Aro.apk", releaseNoteText("**Fixed** `playlists` and [Aro.apk](https://example.org/a.apk)").text)
        val unsafe = releaseNoteText("[Open](javascript:alert)")
        assertEquals("Open", unsafe.text)
        assertTrue(unsafe.getLinkAnnotations(0, unsafe.length).isEmpty())
    }
    @Test fun `code and escaped table pipes remain readable`() {
        val blocks = parseReleaseNotes("```sh\ncommand --flag\n```\n\n| Name | Value |\n| --- | --- |\n| A\\|B | C |")
        assertEquals(ReleaseNoteBlock.Code("command --flag"), blocks.first())
        assertEquals(listOf("A|B", "C"), (blocks.last() as ReleaseNoteBlock.Table).rows.single())
    }
}
