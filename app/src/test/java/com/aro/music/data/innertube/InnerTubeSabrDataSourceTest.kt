package com.aro.music.data.innertube

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import io.mockk.*
import kotlinx.coroutines.flow.flow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

class InnerTubeSabrDataSourceTest {
    private val engine = mockk<InnerTubeEngine>()
    private fun spec(position: Long = 0): DataSpec {
        val uri = mockk<Uri>()
        every { uri.host } returns "seed"
        every { uri.scheme } returns "sabr"
        return DataSpec.Builder().setUri(uri).setPosition(position).build()
    }

    @Test fun `SABR chunks are read as audio bytes with seek offsets`() {
        coEvery { engine.sabrAudio("seed") } returns flow {
            emit(byteArrayOf(1, 2)); emit(byteArrayOf(3, 4, 5))
        }
        val source = InnerTubeSabrDataSource(engine)
        source.open(spec(3))
        val buffer = ByteArray(8)
        assertEquals(2, source.read(buffer, 0, 8))
        assertArrayEquals(byteArrayOf(4, 5), buffer.take(2).toByteArray())
        assertEquals(C.RESULT_END_OF_INPUT, source.read(buffer, 0, 8))
        source.close()
    }

    @Test fun `SABR network failures reach the player`() {
        coEvery { engine.sabrAudio("seed") } returns flow { throw IOException("transport failed") }
        val source = InnerTubeSabrDataSource(engine)
        source.open(spec())
        assertThrows(IOException::class.java) { source.read(ByteArray(4), 0, 4) }
        source.close()
    }
}
