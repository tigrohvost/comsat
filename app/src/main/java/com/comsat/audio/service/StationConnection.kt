package com.comsat.audio.service

import java.net.URI

/**
 * Which requests belong to the tuned track-chained station (Rain Radio). They share one kept
 * connection, while everything else in the app opens a fresh one per request (see AppModule).
 */
internal class StationRoute {
    @Volatile private var host: String? = null

    /** The station stays the station after it is stopped: a late request keeps its path. */
    fun tuneTo(apiBase: String) {
        runCatching { URI(apiBase.trim()).host }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { host = it.lowercase() }
    }

    fun owns(requestHost: String): Boolean = host?.let { it == requestHost.lowercase() } ?: false
}

/**
 * One question to the station, asked again in parallel while the earlier tries stay silent.
 * The first answer wins and the other tries are cancelled; null means every try failed. This
 * is safe because the station's questions can be repeated: its clock is a read, and a track
 * selection carries an id under which the station gives the same answer again.
 *
 * A try gets a connection of its own only while the station has no kept one: that is the
 * start, where a lost handshake would otherwise cost a read timeout. Over a kept connection
 * that has died silently all tries share its fate until it is dropped.
 *
 * Not thread-safe: [ask], [cancel], the verdict passed to [launch] and the action passed to
 * [later] must all run on one thread.
 *
 * @param launch starts one try and returns its cancel; the try reports an answer, or null when
 *   it failed, through the function it is given.
 */
internal class StationQuestion<T : Any>(
    private val tries: Int,
    private val staggerMs: Long,
    private val later: (delayMs: Long, action: () -> Unit) -> Unit,
    private val launch: (verdict: (T?) -> Unit) -> () -> Unit,
    private val answer: (T?) -> Unit,
) {
    private var started = 0
    private var failed = 0
    private var closed = false
    private val cancels = ArrayList<() -> Unit>(tries)

    fun ask() = startTry()

    fun cancel() {
        if (closed) return
        closed = true
        cancels.forEach { it() }
    }

    private fun startTry() {
        if (closed || started >= tries) return
        val index = ++started
        var reported = false
        val cancelTry = launch { result ->
            if (!reported) {
                reported = true
                onVerdict(result)
            }
        }
        // A try that answered at once has already closed the question.
        if (closed) return cancelTry()
        cancels += cancelTry
        if (index < tries) later(staggerMs) { if (started == index) startTry() }
    }

    private fun onVerdict(result: T?) {
        if (closed) return
        if (result != null) return finish(result)
        failed++
        when {
            failed >= tries -> finish(null)
            // Nothing is on its way any more: ask again now instead of waiting out the stagger.
            failed == started -> startTry()
        }
    }

    private fun finish(result: T?) {
        closed = true
        cancels.forEach { it() }
        answer(result)
    }
}
