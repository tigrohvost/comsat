package com.comsat.audio.service

import org.junit.Assert.*
import org.junit.Test

class StationConnectionTest {
    @Test fun `only the tuned station's host rides the kept connection`() {
        val route = StationRoute()
        assertFalse(route.owns("rain-radio.example.net"))
        route.tuneTo("https://Rain-Radio.example.net/")
        assertTrue(route.owns("rain-radio.example.net"))
        assertTrue(route.owns("RAIN-RADIO.EXAMPLE.NET"))
        assertFalse(route.owns("d.liveatc.net"))
        assertFalse(route.owns("example.net"))
        // An address that is not one leaves the station as it was.
        route.tuneTo("not an address")
        route.tuneTo("")
        assertTrue(route.owns("rain-radio.example.net"))
        route.tuneTo("https://other.example.org")
        assertTrue(route.owns("other.example.org"))
        assertFalse(route.owns("rain-radio.example.net"))
    }

    /** A hand-driven clock and network: tries are answered and timers fired by the test. */
    private class Bench(tries: Int = 3, staggerMs: Long = 2_500) {
        val verdicts = mutableListOf<(String?) -> Unit>()
        val cancelled = mutableListOf<Int>()
        val timers = mutableListOf<Pair<Long, () -> Unit>>()
        val answers = mutableListOf<String?>()
        val question = StationQuestion<String>(
            tries, staggerMs,
            later = { delay, action -> timers += delay to action },
            launch = { verdict ->
                val index = verdicts.size
                verdicts += verdict
                ({ cancelled += index })
            },
            answer = { answers += it },
        )
        fun fireTimers() = timers.toList().also { timers.clear() }.forEach { it.second() }
    }

    @Test fun `an answer from the first try ends the question without a second try`() {
        val bench = Bench()
        bench.question.ask()
        assertEquals(1, bench.verdicts.size)
        assertEquals(listOf(2_500L), bench.timers.map { it.first })
        bench.verdicts[0]("clock")
        assertEquals(listOf<String?>("clock"), bench.answers)
        bench.fireTimers()
        assertEquals(1, bench.verdicts.size)
    }

    @Test fun `a silent try is joined by another and the first answer wins`() {
        val bench = Bench()
        bench.question.ask()
        bench.fireTimers()
        assertEquals(2, bench.verdicts.size)
        bench.verdicts[1]("second")
        assertEquals(listOf<String?>("second"), bench.answers)
        // Both tries are cancelled: the silent one for good, the winner harmlessly.
        assertEquals(listOf(0, 1), bench.cancelled)
        // The slow first try answering later changes nothing, and no third try starts.
        bench.verdicts[0]("first")
        bench.fireTimers()
        assertEquals(listOf<String?>("second"), bench.answers)
        assertEquals(2, bench.verdicts.size)
    }

    @Test fun `a failed try is replaced at once and three failures are a failure`() {
        val bench = Bench()
        bench.question.ask()
        bench.verdicts[0](null)
        assertEquals(2, bench.verdicts.size)
        assertTrue(bench.answers.isEmpty())
        bench.verdicts[1](null)
        assertEquals(3, bench.verdicts.size)
        bench.verdicts[2](null)
        assertEquals(listOf<String?>(null), bench.answers)
        // The stagger timers of the earlier tries start nothing more.
        bench.fireTimers()
        assertEquals(3, bench.verdicts.size)
    }

    @Test fun `one failure among silent tries waits for the others`() {
        val bench = Bench()
        bench.question.ask()
        bench.fireTimers()                       // try 2 joins the silent try 1
        bench.verdicts[0](null)                  // try 1 fails while try 2 is still on its way
        assertEquals(2, bench.verdicts.size)
        assertTrue(bench.answers.isEmpty())
        bench.fireTimers()                       // try 2 stays silent too: try 3 joins
        assertEquals(3, bench.verdicts.size)
        bench.verdicts[2]("third")
        assertEquals(listOf<String?>("third"), bench.answers)
    }

    @Test fun `no more tries than allowed and one verdict per try`() {
        val bench = Bench(tries = 2)
        bench.question.ask()
        bench.fireTimers()
        bench.fireTimers()
        assertEquals(2, bench.verdicts.size)
        bench.verdicts[0](null)
        bench.verdicts[0](null)                  // a try reporting twice counts once
        assertTrue(bench.answers.isEmpty())
        bench.verdicts[1](null)
        assertEquals(listOf<String?>(null), bench.answers)
    }

    @Test fun `a cancelled question answers nothing and cancels what is running`() {
        val bench = Bench()
        bench.question.ask()
        bench.fireTimers()
        bench.question.cancel()
        assertEquals(listOf(0, 1), bench.cancelled)
        bench.verdicts[0]("late")
        bench.verdicts[1](null)
        bench.fireTimers()
        assertTrue(bench.answers.isEmpty())
        assertEquals(2, bench.verdicts.size)
        bench.question.cancel()
        assertEquals(listOf(0, 1), bench.cancelled)
    }

    @Test fun `a try that answers before it returns closes the question cleanly`() {
        val answers = mutableListOf<String?>()
        var cancels = 0
        val timers = mutableListOf<() -> Unit>()
        val question = StationQuestion<String>(
            3, 2_500,
            later = { _, action -> timers += action },
            launch = { verdict -> verdict("at once"); ({ cancels++ }) },
            answer = { answers += it },
        )
        question.ask()
        assertEquals(listOf<String?>("at once"), answers)
        assertEquals(1, cancels)
        assertTrue(timers.isEmpty())
    }
}
