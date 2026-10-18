package com.github.marcoral.versatia.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class ConditionsTest {
    @Test
    fun `awaitUntil returns at once when the condition holds and waits otherwise`() = runTicking { clock ->
        val ready = MutableStateFlow(true)
        awaitUntil(ready)
        assertEquals(0L, clock.currentTick)
        ready.value = false
        launch {
            delayTicks(4)
            ready.value = true
        }
        awaitUntil(ready)
        assertEquals(4L, clock.currentTick)
    }

    @Test
    fun `runOnceWhile returns true when the block completes`() = runTicking { clock ->
        assertTrue(runOnceWhile(MutableStateFlow(true)) { delayTicks(3) })
        assertEquals(3L, clock.currentTick)
    }

    @Test
    fun `runOnceWhile cancels the block as soon as the condition turns false`() = runTicking { clock ->
        val holds = MutableStateFlow(true)
        launch {
            delayTicks(5)
            holds.value = false
        }
        var cleanedUp = false
        val completed = runOnceWhile(holds) {
            try {
                delayTicks(100)
            } finally {
                cleanedUp = true
            }
        }
        assertFalse(completed)
        assertTrue(cleanedUp)
        assertEquals(5L, clock.currentTick)
    }

    @Test
    fun `runOnceWhile never starts the block when the condition does not hold`() = runTicking {
        var started = false
        assertFalse(runOnceWhile(MutableStateFlow(false)) { started = true })
        assertFalse(started)
    }

    @Test
    fun `runOnceWhile passes on cancellation and failures`() = runTicking {
        var cleanedUp = false
        val job = launch {
            runOnceWhile(MutableStateFlow(true)) {
                try {
                    awaitCancellation()
                } finally {
                    cleanedUp = true
                }
            }
        }
        delayTicks(2)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(cleanedUp)

        assertEquals("block", assertFailsWith<IllegalStateException> { runOnceWhile(MutableStateFlow(true)) { error("block") } }.message)
        assertEquals("condition", assertFailsWith<IllegalStateException> { runOnceWhile(flow { error("condition") }) { delayTicks(10) } }.message)
    }

    @Test
    fun `whenever starts the block every time the condition turns true and cancels it when it turns false`() = runTicking { clock ->
        val on = MutableStateFlow(false)
        val events = mutableListOf<String>()
        val job = launch {
            whenever(on) {
                events += "start ${clock.currentTick}"
                try {
                    awaitCancellation()
                } finally {
                    events += "stop ${clock.currentTick}"
                }
            }
        }
        delayTicks(3)
        on.value = true
        delayTicks(3)
        on.value = false
        delayTicks(3)
        on.value = true
        delayTicks(2)
        job.cancel()
        job.join()
        assertEquals(listOf("start 3", "stop 6", "start 9", "stop 11"), events)
    }

    @Test
    fun `whenever does not restart a completed block while the condition stays true`() = runTicking {
        val on = MutableStateFlow(true)
        var runs = 0
        val job = launch { whenever(on) { runs++ } }
        delayTicks(5)
        assertEquals(1, runs)
        on.value = false
        delayTicks(2)
        on.value = true
        delayTicks(2)
        assertEquals(2, runs)
        job.cancel()
    }

    @Test
    fun `whenever works with a Condition`() = runTicking {
        var open = false
        val gate = condition { open }
        var runs = 0
        val job = launch { whenever(gate) { runs++ } }
        delayTicks(1)
        assertEquals(0, runs)
        open = true
        gate.recheck()
        delayTicks(1)
        assertEquals(1, runs)
        job.cancel()
    }
}
