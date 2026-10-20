package com.github.marcoral.versatia.task

import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/** A [ServerClock] counting ticks in the virtual time of [scheduler], one tick every [TICK_MILLIS]; its conditions ignore triggers and recheck at once. */
@OptIn(ExperimentalCoroutinesApi::class)
class VirtualServerClock(private val scheduler: TestCoroutineScheduler) : ServerClock {
    val failures = mutableListOf<Throwable>()

    override val currentTick get() = scheduler.currentTime / TICK_MILLIS
    override val timeSource = scheduler.timeSource

    override suspend fun delayTicks(ticks: Long) = delay((currentTick + max(ticks, 1)) * TICK_MILLIS - scheduler.currentTime)

    override fun condition(triggers: List<RecheckTrigger>, check: () -> Boolean, owner: Job): Condition = ManualCondition(check)

    override suspend fun reportFailedRun(failure: Throwable) {
        failures += failure
    }

    companion object {
        const val TICK_MILLIS = 50L
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class ManualCondition(private val check: () -> Boolean, private val state: MutableStateFlow<Boolean> = MutableStateFlow(check())) :
    Condition, StateFlow<Boolean> by state {
    private var closed = false

    override fun recheck() {
        if (!closed) state.value = check()
    }

    override fun close() {
        closed = true
    }
}

/** Runs [body] in virtual time with a [VirtualServerClock] in its context. */
fun runTicking(body: suspend CoroutineScope.(VirtualServerClock) -> Unit) = runTest {
    val clock = VirtualServerClock(testScheduler)
    withContext(clock) { body(clock) }
}
