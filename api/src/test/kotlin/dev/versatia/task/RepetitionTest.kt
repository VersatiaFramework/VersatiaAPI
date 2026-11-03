package dev.versatia.task

import dev.versatia.task.VirtualServerClock.Companion.TICK_MILLIS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class RepetitionTest {
    /** The ticks at which everyTicks ran, left running for [ticks] and a half; [action] gets the number of the run. */
    private fun runsOf(ticks: Long, period: Long, initialDelay: Long = period, action: suspend (Int) -> Unit = {}): List<Long> {
        val runs = mutableListOf<Long>()
        runTicking { clock ->
            val job = launch {
                everyTicks(period, initialDelay) {
                    runs += clock.currentTick
                    action(runs.size)
                }
            }
            delay(ticks * TICK_MILLIS + TICK_MILLIS / 2)
            job.cancel()
        }
        return runs
    }

    @Test
    fun `everyTicks waits one period before the first run by default`() {
        assertEquals(listOf(5L, 10L, 15L), runsOf(ticks = 17, period = 5))
    }

    @Test
    fun `everyTicks runs at once without a delay`() {
        assertEquals(listOf(0L, 5L, 10L, 15L), runsOf(ticks = 17, period = 5, initialDelay = 0))
    }

    @Test
    fun `everyTicks skips the slots missed while the action suspended`() {
        assertEquals(listOf(5L, 20L, 25L), runsOf(ticks = 27, period = 5) { if (it == 1) delayTicks(12) })
    }

    @Test
    fun `everyTicks runs the slot due the moment the action returns at once`() {
        assertEquals(listOf(5L, 10L, 15L), runsOf(ticks = 17, period = 5) { if (it == 1) delayTicks(5) })
    }

    @Test
    fun `everyTicks validates its arguments`() = runTicking {
        assertEquals("The period must be at least one tick, got 0", assertFailsWith<IllegalArgumentException> { everyTicks(0) {} }.message)
        assertEquals("The delay must not be negative, got -1 ticks", assertFailsWith<IllegalArgumentException> { everyTicks(5, -1) {} }.message)
    }

    @Test
    fun `a failing run is reported and the next one follows on schedule`() = runTicking { clock ->
        val runs = mutableListOf<Long>()
        val job = launch {
            everyTicks(5) {
                runs += clock.currentTick
                if (runs.size == 1) error("boom")
            }
        }
        delay(17 * TICK_MILLIS)
        job.cancel()
        assertEquals(listOf(5L, 10L, 15L), runs)
        assertEquals("boom", clock.failures.single().message)
    }

    @Test
    fun `the same failure is reported once until a run succeeds`() = runTicking { clock ->
        var run = 0
        val job = launch {
            everyTicks(1) {
                run++
                if (run <= 2) error("boom")
                if (run == 3 || run == 5) throw IllegalArgumentException("other")
            }
        }
        delay(5 * TICK_MILLIS + TICK_MILLIS / 2)
        job.cancel()
        assertEquals(5, run)
        assertEquals(listOf("boom", "other", "other"), clock.failures.map { it.message })
    }

    @Test
    fun `cancellation ends the loop and is not reported`() = runTicking { clock ->
        var runs = 0
        val job = launch {
            everyTicks(2) {
                runs++
                awaitCancellation()
            }
        }
        delay(5 * TICK_MILLIS)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, runs)
        assertEquals(emptyList(), clock.failures)
    }

    @Test
    fun `a timeout inside the action is reported and the loop goes on`() = runTicking { clock ->
        var runs = 0
        val job = launch {
            everyTicks(2) {
                runs++
                withTimeout(10.milliseconds) { awaitCancellation() }
            }
        }
        delay(7 * TICK_MILLIS)
        job.cancel()
        assertEquals(3, runs)
        assertIs<TimeoutCancellationException>(clock.failures.single())
    }

    @Test
    fun `every keeps a fixed rate in wall-clock time`() = runTicking { clock ->
        val start = clock.timeSource.markNow()
        val runs = mutableListOf<Duration>()
        val job = launch { every(1.seconds) { runs += start.elapsedNow() } }
        delay(3500.milliseconds)
        job.cancel()
        assertEquals(listOf(1.seconds, 2.seconds, 3.seconds), runs)
    }

    @Test
    fun `every skips the slots missed during a long action`() = runTicking { clock ->
        val start = clock.timeSource.markNow()
        val runs = mutableListOf<Duration>()
        val job = launch {
            every(1.seconds) {
                runs += start.elapsedNow()
                if (runs.size == 1) delay(2500.milliseconds)
            }
        }
        delay(5500.milliseconds)
        job.cancel()
        assertEquals(listOf(1.seconds, 4.seconds, 5.seconds), runs)
    }

    @Test
    fun `every validates its arguments`() = runTicking {
        assertEquals("The period must be positive, got 0s", assertFailsWith<IllegalArgumentException> { every(Duration.ZERO) {} }.message)
        assertEquals("The delay must not be negative, got -1s", assertFailsWith<IllegalArgumentException> { every(1.seconds, (-1).seconds) {} }.message)
    }
}
