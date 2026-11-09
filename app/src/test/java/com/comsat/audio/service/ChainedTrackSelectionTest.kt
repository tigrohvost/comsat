package com.comsat.audio.service

import org.junit.Assert.*
import org.junit.Test

class ChainedTrackSelectionTest {
    @Test fun `lost response and pause retry the identical selection URL`() {
        val selection = ChainedTrackSelection("one & two.mp3", "test")
        assertEquals("https://radio.example/listener-status", selection.url("https://radio.example/"))
        assertTrue(selection.synchronize(1794228645000, null))
        val url = selection.url("https://radio.example")
        assertTrue(url.contains("request_id=test%3A1794228645000%3A0"))
        assertTrue(url.contains("current=one%20%26%20two.mp3"))
        assertTrue(selection.synchronize(1794228999000, null))
        assertEquals(url, selection.url("https://radio.example"))
    }

    @Test fun `legacy station can issue time through HTTP Date without using phone clock`() {
        val selection = ChainedTrackSelection(null, "legacy")
        assertTrue(selection.synchronize(null, "Mon, 09 Nov 2026 12:50:45 GMT"))
        assertEquals("legacy:1794228645000:0", selection.requestId)
        assertFalse(selection.url("https://radio.example").contains("current="))
    }

    @Test fun `missing or invalid server clock never allocates a selection`() {
        val selection = ChainedTrackSelection(null)
        assertFalse(selection.synchronize(0, "invalid"))
        assertNull(selection.requestId)
        assertEquals("https://radio.example/listener-status", selection.url("https://radio.example"))
    }

    @Test fun `next logical selection gets a different id at the same server time`() {
        val first = ChainedTrackSelection(null)
        val next = ChainedTrackSelection("previous.mp3")
        first.synchronize(1794228645000, null)
        next.synchronize(1794228645000, null)
        assertNotEquals(first.requestId, next.requestId)
    }
}
