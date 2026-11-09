package com.comsat.audio.service

/**
 * What a track-chained station (Rain Radio) offers beside its music files: it counts who is
 * listening, and its host speaks over the music in short voice clips. The web player does both;
 * without them the station hears nobody and the listener never hears the host.
 *
 * Pure logic only, so that it is covered by JVM tests; the HTTP calls live in AudioService.
 */

/** One spoken line of the station's host. */
internal data class SayClip(val seq: Long, val url: String, val text: String)

/** The voice feed: starts live and hands out every later clip exactly once, in order. */
internal class SayFeed {
    var ready = false
        private set
    var lastSeq = 0L
        private set

    /** The first answer only marks where "now" is: stored history is never replayed. */
    fun startLive(latest: Long) {
        lastSeq = maxOf(0L, latest)
        ready = true
    }

    fun accept(clips: List<SayClip>): List<SayClip> {
        if (!ready) return emptyList()
        val fresh = clips.filter { it.seq > lastSeq && it.url.isNotBlank() }
            .sortedBy { it.seq }
            .distinctBy { it.seq }
        fresh.lastOrNull()?.let { lastSeq = it.seq }
        return fresh
    }

    fun reset() {
        ready = false
        lastSeq = 0L
    }

    /** Query of the next request: the feed start, or everything after the last clip heard. */
    fun path(): String = if (ready) "/say-feed?after=$lastSeq" else "/say-feed"
}

/** The station's listener token: issued as a cookie with its page, echoed back in a header. */
internal object ListenerToken {
    const val COOKIE = "rr_listener_token"
    const val HEADER = "X-Rain-Radio-Token"
    private val VALUE = Regex("(?:^|;\\s*)$COOKIE=([A-Za-z0-9_-]{32,128})(?:;|$)")

    fun fromSetCookie(headers: List<String>): String? =
        headers.firstNotNullOfOrNull { VALUE.find(it)?.groupValues?.get(1) }
}

/** A station path joined to its base; clip and track addresses arrive as absolute paths. */
internal fun stationUrl(apiBase: String, path: String): String =
    if (path.startsWith("http://") || path.startsWith("https://")) path
    else apiBase.trimEnd('/') + "/" + path.trimStart('/')
