package com.comsat.audio.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChainedTrackUrlTest {

    @Test
    fun `resolves root-relative track on station host`() {
        assertEquals(
            "http://192.144.15.152:8788/static/tracks/one.mp3",
            resolveChainedTrackUrl(
                "http://192.144.15.152:8788",
                "/static/tracks/one.mp3"
            )
        )
    }

    @Test
    fun `resolves relative track below normalized base`() {
        assertEquals(
            "https://radio.example/api/tracks/two.mp3",
            resolveChainedTrackUrl("https://radio.example/api", "tracks/two.mp3")
        )
    }

    @Test
    fun `preserves absolute CDN track URL`() {
        assertEquals(
            "https://cdn.example/three.mp3",
            resolveChainedTrackUrl(
                "https://radio.example/api",
                "https://cdn.example/three.mp3"
            )
        )
    }

    @Test
    fun `rejects empty or non-http URL`() {
        assertNull(resolveChainedTrackUrl("https://radio.example", "   "))
        assertNull(resolveChainedTrackUrl("https://radio.example", "file:///tmp/four.mp3"))
    }
}
