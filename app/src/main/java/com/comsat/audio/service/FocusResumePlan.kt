package com.comsat.audio.service

/** Remembers only the streams interrupted by a temporary loss of audio focus. */
internal class FocusResumePlan {
    data class Streams(val atc: Boolean = false, val soma: Boolean = false)

    private var pending = Streams()

    fun remember(atc: Boolean, soma: Boolean) {
        // Repeated loss callbacks must not forget streams already paused by us.
        pending = Streams(pending.atc || atc, pending.soma || soma)
    }

    fun cancelAtc() { pending = pending.copy(atc = false) }

    fun cancelSoma() { pending = pending.copy(soma = false) }

    fun clear() { pending = Streams() }

    fun take(): Streams = pending.also { clear() }
}
