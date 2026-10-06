package com.aro.music.data.innertube

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/** Selects a transport after ResolvingDataSource replaces the logical track URI. */
class InnerTubeTransportDataSource(
    private val ordinary: DataSource,
    private val sabr: DataSource,
) : DataSource {
    private var active: DataSource? = null
    override fun addTransferListener(listener: TransferListener) {
        ordinary.addTransferListener(listener)
        sabr.addTransferListener(listener)
    }
    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme == "sabr") sabr else ordinary
        active = source
        return source.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(active).read(buffer, offset, length)
    override fun getUri(): Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()
    override fun close() {
        active?.close()
        active = null
    }
}
