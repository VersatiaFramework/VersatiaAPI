package com.github.marcoral.versatia.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ServerClockTest {
    @Test
    fun `delayTicks resumes after the given number of ticks`() = runTicking { clock ->
        delayTicks(3)
        assertEquals(3L, clock.currentTick)
        delayTicks(0)
        assertEquals(4L, clock.currentTick, "zero waits for the next tick")
        delayTick()
        assertEquals(5L, clock.currentTick)
        val configured = 2
        delayTicks(configured)
        assertEquals(7L, clock.currentTick, "an Int needs no conversion")
    }

    @Test
    fun `delayTicks rejects a negative delay`() = runTicking {
        val failure = assertFailsWith<IllegalArgumentException> { delayTicks(-1) }
        assertEquals("A delay must not be negative, got -1 ticks", failure.message)
    }

    @Test
    fun `condition is evaluated at once and again on recheck`() = runTicking {
        var open = false
        val gate = condition { open }
        assertFalse(gate.value)
        open = true
        assertFalse(gate.value, "nothing is polled")
        gate.recheck()
        assertTrue(gate.value)
    }

    @Test
    fun `the helpers need a server clock`() = runTest {
        val failure = assertFailsWith<IllegalStateException> { delayTicks(1) }
        assertEquals(
            "No server clock in this coroutine: launch it from an injected PluginScope or run it in a BackgroundTask; runBlocking and GlobalScope coroutines have none",
            failure.message,
        )
        assertFailsWith<IllegalStateException> { condition { true } }
    }
}
