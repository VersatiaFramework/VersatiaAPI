package dev.versatia.task

import dev.versatia.VersatiaInternalApi
import dev.versatia.util.validation.validateAtLeast
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

/**
 * The server's clock in the context of every coroutine of a Versatia plugin, where the functions
 * of this package find it. VersatiaCore implements it; plugin code calls the top-level functions
 * and never implements it, except as a test fixture.
 */
@VersatiaInternalApi
interface ServerClock : CoroutineContext.Element {
    /** Server ticks counted by the runtime; only differences mean something. */
    val currentTick: Long

    /** What wall-clock helpers measure with: the monotonic clock on a server, virtual time in a test. */
    val timeSource: TimeSource.WithComparableMarks

    /** Suspends until [ticks] more ticks have begun, at least one, and resumes in the caller's dispatcher. */
    suspend fun delayTicks(ticks: Long)

    /** A condition of the plugin whose listeners go when [owner] completes; see [PluginScope.condition]. */
    fun condition(triggers: List<RecheckTrigger>, check: () -> Boolean, owner: Job): Condition

    /** Reports a failed run of a repeating helper, which then goes on. */
    suspend fun reportFailedRun(failure: Throwable)

    override val key get() = Key

    companion object Key : CoroutineContext.Key<ServerClock>
}

/**
 * Suspends for [ticks] server ticks: game time, which a lagging server stretches; `delay` waits
 * wall-clock time. Zero waits for the next tick, like Bukkit's `runTask`, so a loop around it never
 * freezes the server; a negative value is an error.
 */
suspend fun delayTicks(ticks: Long) = serverClock().delayTicks(ticks.validateAtLeast(0) { "A delay must not be negative, got $ticks ticks" })

/** [delayTicks] for an `Int`, such as a value read from configuration. */
suspend fun delayTicks(ticks: Int) = delayTicks(ticks.toLong())

/** Suspends until the next server tick; the same as `delayTicks(1)`. */
suspend fun delayTick() = delayTicks(1)

/**
 * A condition of the plugin bound to the calling coroutine: [check] is evaluated on the server
 * thread now, then again whenever one of [triggers] fires or [Condition.recheck] is called, and its
 * listeners go when the coroutine completes. See [PluginScope.condition].
 */
suspend fun condition(vararg triggers: RecheckTrigger, check: () -> Boolean) =
    serverClock().condition(triggers.toList(), check, currentCoroutineContext().job)

internal suspend fun serverClock() = currentCoroutineContext()[ServerClock]
    ?: error("No server clock in this coroutine: launch it from an injected PluginScope or run it in a BackgroundTask; runBlocking and GlobalScope coroutines have none")
