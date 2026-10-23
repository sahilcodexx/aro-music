package com.aro.music.data.innertube

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DownloadExtractionPolicyTest {
    @Test fun `downloads require standalone audio transports while playback keeps HLS`() {
        val download = extractionHints(forDownload = true)
        assertFalse(download.allowHls)
        assertTrue(download.allowSabr)
        assertFalse(download.wantVideo)
        assertTrue(extractionHints(forDownload = false).allowHls)
    }
}
