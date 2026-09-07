package com.comsat.audio.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamStateTest {
    @Test
    fun `playback can be cancelled while tuning or reconnecting`() {
        for (status in listOf(StreamStatus.LOADING, StreamStatus.RECONNECTING,
            StreamStatus.BUFFERING, StreamStatus.PLAYING)) {
            assertTrue(status.name, StreamState(status).canPause)
        }
    }

    @Test
    fun `idle paused and failed channels offer playback instead`() {
        for (status in listOf(StreamStatus.IDLE, StreamStatus.PAUSED, StreamStatus.ERROR)) {
            assertFalse(status.name, StreamState(status).canPause)
        }
        assertFalse(StreamState(StreamStatus.RECONNECTING).isActive)
    }
}
