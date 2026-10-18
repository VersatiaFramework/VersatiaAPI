package com.github.marcoral.versatia.task

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import org.bukkit.event.Event
import org.bukkit.event.EventPriority

/**
 * The last result of a check, evaluated again only when a [RecheckTrigger] fires or [recheck] is
 * called, never by polling, so the check may be expensive. A state, not a stream of events: changes
 * that cancel each other out before the check runs again are not seen. Created by
 * [PluginScope.condition] or, bound to a coroutine, by [condition]; consumed by [whenever],
 * [runOnceWhile] and [awaitUntil] like any `Flow<Boolean>`. See docs/enhancements.md.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
interface Condition : StateFlow<Boolean> {
    /**
     * Evaluates the check again: at once when called on the server thread, so [value] is current
     * when this returns, otherwise at the next tick. Call it right after changing what the check reads.
     */
    fun recheck()
}

/**
 * A Bukkit event that makes a [Condition] evaluate its check again; built with [recheckOn]. Events
 * of type [event] that pass [filter] trigger it; the filter runs on the event's thread and must be
 * cheap and thread-safe.
 */
class RecheckTrigger @PublishedApi internal constructor(
    val event: Class<out Event>,
    val priority: EventPriority,
    val ignoreCancelled: Boolean,
    val immediately: Boolean,
    val filter: (Event) -> Boolean,
)

/**
 * Evaluates a condition again after every event of type [E] passing [filter]. By default the check
 * runs once at the next tick, however many events came, on the server thread: most Bukkit events
 * fire before their change is applied (a moving player is still at the old location, a quitting
 * one still online). With [immediately] the check runs inside the handler, for events that fire
 * after their change, such as `PlayerJoinEvent`; an event fired on another thread is still checked
 * at the next tick, and a warning says so.
 */
inline fun <reified E : Event> recheckOn(
    priority: EventPriority = EventPriority.MONITOR,
    ignoreCancelled: Boolean = true,
    immediately: Boolean = false,
    noinline filter: (E) -> Boolean = { true },
) = RecheckTrigger(E::class.java, priority, ignoreCancelled, immediately) { filter(it as E) }
