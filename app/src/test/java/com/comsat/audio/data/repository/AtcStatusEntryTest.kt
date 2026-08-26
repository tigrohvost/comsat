package com.comsat.audio.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtcStatusEntryTest {

    private val ttl = STATUS_TTL_MILLIS

    @Test
    fun `fresh inside the ttl, stale after it`() {
        val e = AtcStatusEntry(mount = "kjfk_twr", checkedAt = 1_000_000L)
        assertTrue(e.isFresh(now = 1_000_000L))
        assertTrue(e.isFresh(now = 1_000_000L + ttl))
        assertFalse(e.isFresh(now = 1_000_000L + ttl + 1))
    }

    @Test
    fun `clock going backwards is treated as stale`() {
        val e = AtcStatusEntry(mount = null, checkedAt = 5_000_000L)
        assertFalse(e.isFresh(now = 4_999_999L))
    }

    @Test
    fun `offline result is cached the same way as online`() {
        val e = AtcStatusEntry(mount = null, checkedAt = 0L)
        assertTrue(e.isFresh(now = ttl / 2))
    }
}
