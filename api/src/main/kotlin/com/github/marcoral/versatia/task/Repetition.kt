package com.github.marcoral.versatia.task

import com.github.marcoral.versatia.util.validation.validateAtLeast
import com.github.marcoral.versatia.util.validation.validateGreaterThan
import kotlin.math.ceil
import kotlin.time.Duration
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Runs [action] every [periodTicks] ticks at a fixed rate in server time, the first time after
 * [delayTicks] ticks, until the coroutine is cancelled. Slots missed while [action] suspended are
 * skipped, never caught up; a slot due the moment [action] returns runs at once. A run that throws
 * is reported and the next one follows on schedule; the same failure is reported once until a run
 * succeeds.
 */
suspend fun everyTicks(periodTicks: Long, delayTicks: Long = periodTicks, action: suspend () -> Unit): Nothing {
    periodTicks.validateAtLeast(1) { "The period must be at least one tick, got $periodTicks" }
    delayTicks.validateAtLeast(0) { "The delay must not be negative, got $delayTicks ticks" }
    val clock = serverClock()
    val runs = Runs(clock)
    var due = clock.currentTick + delayTicks
    while (true) {
        val wait = due - clock.currentTick
        if (wait > 0) clock.delayTicks(wait)
        runs.run(action)
        due += periodTicks
        val now = clock.currentTick
        if (due < now) due += (now - due + periodTicks - 1) / periodTicks * periodTicks
    }
}

/** [everyTicks] for `Int` ticks, such as values read from configuration. */
suspend fun everyTicks(periodTicks: Int, delayTicks: Int = periodTicks, action: suspend () -> Unit): Nothing =
    everyTicks(periodTicks.toLong(), delayTicks.toLong(), action)

/** [everyTicks] in wall-clock time: every [period], the first time after [delay]; slots missed during a freeze are skipped. */
suspend fun every(period: Duration, delay: Duration = period, action: suspend () -> Unit): Nothing {
    period.validateGreaterThan(Duration.ZERO) { "The period must be positive, got $period" }
    delay.validateAtLeast(Duration.ZERO) { "The delay must not be negative, got $delay" }
    val clock = serverClock()
    val runs = Runs(clock)
    var due = clock.timeSource.markNow() + delay
    while (true) {
        kotlinx.coroutines.delay(-due.elapsedNow())
        runs.run(action)
        due += period
        val late = due.elapsedNow()
        if (late.isPositive()) due += period * ceil(late / period)
    }
}

/** One run at a time: a failure is reported unless it repeats the previous one, a success forgets it. */
private class Runs(private val clock: ServerClock) {
    private var lastFailure: String? = null

    suspend fun run(action: suspend () -> Unit) {
        try {
            action()
            lastFailure = null
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            val signature = "${e.javaClass.name}: ${e.message}"
            if (signature != lastFailure) clock.reportFailedRun(e)
            lastFailure = signature
        }
    }
}
