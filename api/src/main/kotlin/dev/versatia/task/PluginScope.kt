package dev.versatia.task

import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * The coroutines of the plugin this is injected into, also as `CoroutineScope`: they run on the
 * server thread, starting at once when launched from it, unless they switch with `withContext`;
 * a reload leaves them alone, and the plugin's stop cancels them before its stop functions run.
 * A failing coroutine is logged and does not cancel the others. VersatiaCore exposes it as a
 * prototype, so every plugin, and every module prototype made for it, receives that plugin's
 * instance. Cancel the jobs you launch, never the scope. See docs/enhancements.md.
 */
interface PluginScope : CoroutineScope {
    /**
     * The dispatcher of this scope: the server thread, at once when already on it, otherwise at
     * the next tick. For code outside the scope's coroutines: `withContext(serverThread)` from a
     * coroutine of another scope, `serverThread.asExecutor()` for Java-style futures.
     */
    val serverThread: CoroutineDispatcher

    /**
     * A [Condition] living as long as the plugin: [check] is evaluated on the server thread now,
     * then again whenever one of [triggers] fires and whenever [Condition.recheck] is called. Its
     * listeners go when the plugin stops or [Condition.close] is called. Inside a coroutine, the
     * top-level [condition] binds them to that coroutine instead.
     */
    fun condition(vararg triggers: RecheckTrigger, check: () -> Boolean): Condition

    /**
     * Values per player living as long as the plugin: a player's value goes when they quit, or [keepAfterQuit] later
     * unless they join again meanwhile (a combat tag that outlives a log-out), and all go when the plugin stops.
     */
    fun <T : Any> perPlayer(keepAfterQuit: Duration = Duration.ZERO): PlayerValues<T>
}
