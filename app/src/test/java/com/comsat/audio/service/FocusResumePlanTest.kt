package com.comsat.audio.service

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusResumePlanTest {
    @Test
    fun `focus gain restores only the stream interrupted by focus loss`() {
        val plan = FocusResumePlan()
        // ATC was already paused when a call interrupted the music.
        plan.remember(atc = false, soma = true)
        assertEquals(FocusResumePlan.Streams(soma = true), plan.take())
    }

    @Test
    fun `a second loss callback preserves the interrupted streams`() {
        val plan = FocusResumePlan()
        plan.remember(atc = true, soma = true)
        // The players are now paused when Android repeats the loss callback.
        plan.remember(atc = false, soma = false)
        assertEquals(FocusResumePlan.Streams(atc = true, soma = true), plan.take())
    }

    @Test
    fun `stopping ATC during an interruption still permits music to resume`() {
        val plan = FocusResumePlan()
        plan.remember(atc = true, soma = true)
        plan.cancelAtc()
        assertEquals(FocusResumePlan.Streams(soma = true), plan.take())
    }

    @Test
    fun `stopping music during an interruption still permits ATC to resume`() {
        val plan = FocusResumePlan()
        plan.remember(atc = true, soma = true)
        plan.cancelSoma()
        assertEquals(FocusResumePlan.Streams(atc = true), plan.take())
    }

    @Test
    fun `explicit pause or headphone unplug prevents automatic resume`() {
        val plan = FocusResumePlan()
        plan.remember(atc = true, soma = true)
        plan.clear()
        assertEquals(FocusResumePlan.Streams(), plan.take())
    }

    @Test
    fun `a later focus gain cannot replay a consumed resume request`() {
        val plan = FocusResumePlan()
        plan.remember(atc = true, soma = true)
        plan.take()
        assertEquals(FocusResumePlan.Streams(), plan.take())
    }
}
