package com.comsat.audio.service

import org.junit.Assert.*
import org.junit.Test

class StationLiveLinkTest {
    private fun clip(seq: Long, text: String = "line $seq") = SayClip(seq, "/static/tts/$seq.mp3", text)

    @Test fun `feed starts live and never replays stored history`() {
        val feed = SayFeed()
        assertEquals("/say-feed", feed.path())
        // Before the starting point is known nothing is spoken, whatever the station sends.
        assertTrue(feed.accept(listOf(clip(315), clip(316))).isEmpty())
        feed.startLive(316)
        assertEquals("/say-feed?after=316", feed.path())
        assertTrue(feed.accept(listOf(clip(315), clip(316))).isEmpty())
    }

    @Test fun `every later clip is handed out once and in order`() {
        val feed = SayFeed().apply { startLive(316) }
        val first = feed.accept(listOf(clip(318), clip(317), clip(317)))
        assertEquals(listOf(317L, 318L), first.map { it.seq })
        assertEquals("/say-feed?after=318", feed.path())
        // A repeated answer (a lost response retried) does not speak the line twice.
        assertTrue(feed.accept(listOf(clip(317), clip(318))).isEmpty())
        assertEquals(listOf(319L), feed.accept(listOf(clip(319))).map { it.seq })
    }

    @Test fun `a clip without an address is skipped and a reset starts live again`() {
        val feed = SayFeed().apply { startLive(0) }
        assertEquals(listOf(2L), feed.accept(listOf(SayClip(1, "", "mute"), clip(2))).map { it.seq })
        feed.reset()
        assertFalse(feed.ready)
        assertEquals("/say-feed", feed.path())
        assertTrue(feed.accept(listOf(clip(3))).isEmpty())
    }

    @Test fun `listener token is read from the station cookie only`() {
        val token = "A".repeat(43)
        assertEquals(token, ListenerToken.fromSetCookie(
            listOf("other=1; Path=/", "rr_listener_token=$token; Path=/; Max-Age=86400; SameSite=Strict")))
        assertEquals(token, ListenerToken.fromSetCookie(listOf("a=b; rr_listener_token=$token")))
        // Too short, foreign characters or another cookie name give no token.
        assertNull(ListenerToken.fromSetCookie(listOf("rr_listener_token=short; Path=/")))
        assertNull(ListenerToken.fromSetCookie(listOf("rr_listener_token=${"A".repeat(40)}%3B; Path=/")))
        assertNull(ListenerToken.fromSetCookie(listOf("xrr_listener_token=$token; Path=/")))
        assertNull(ListenerToken.fromSetCookie(emptyList()))
    }

    @Test fun `station paths are joined to the base without doubled slashes`() {
        assertEquals("https://radio.example/say-feed?after=3", stationUrl("https://radio.example/", "/say-feed?after=3"))
        assertEquals("https://radio.example/listener-ping", stationUrl("https://radio.example", "listener-ping"))
        assertEquals("https://cdn.example/a.mp3", stationUrl("https://radio.example", "https://cdn.example/a.mp3"))
    }
}
