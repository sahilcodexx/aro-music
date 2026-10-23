package com.aro.music.presentation.screens.search.components

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class GenreArtworkTest {
    @Test fun `each default genre has its own bundled photo`() {
        assertEquals(14, genrePhotos.size)
        assertEquals(genrePhotos.size, genrePhotos.values.toSet().size)
        genrePhotos.forEach { (genre, photo) ->
            assertEquals(photo, genrePhoto("  ${genre.uppercase(java.util.Locale.ROOT)}  "))
            val file = File("src/main/assets/genre/$photo.webp")
            assertTrue(file.isFile && file.length() > 1000, "Missing photo for $genre")
        }
    }
    @Test fun `unknown genres get stable artwork`() {
        assertEquals(genrePhoto("Experimental"), genrePhoto("experimental"))
        assertTrue(genrePhoto("Experimental") in genrePhotos.values)
    }
}
