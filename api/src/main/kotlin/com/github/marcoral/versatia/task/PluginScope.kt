package com.github.marcoral.versatia.task

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
     * listeners go when the plugin stops. Inside a coroutine, the top-level [condition] binds them
     * to that coroutine instead.
     */
    fun condition(vararg triggers: RecheckTrigger, check: () -> Boolean): Condition
}
