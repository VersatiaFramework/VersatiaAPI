package com.github.marcoral.versatia.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Suspends until [condition] is true; returns at once when it already is. */
suspend fun awaitUntil(condition: Flow<Boolean>) {
    condition.first { it }
}

/**
 * Runs [block] once, cancelling it as soon as [condition] turns false; returns whether the block
 * completed. When the condition is false already, the block never starts. A block or condition
 * that throws fails the call. For work that should start again every time the condition holds, see
 * [whenever].
 */
suspend fun runOnceWhile(condition: Flow<Boolean>, block: suspend CoroutineScope.() -> Unit): Boolean {
    if (!condition.first()) return false
    var lost = false
    coroutineScope {
        val work = launch(start = CoroutineStart.LAZY, block = block)
        val watch = launch(start = CoroutineStart.LAZY) {
            condition.first { !it }
            lost = true
            work.cancel()
        }
        work.invokeOnCompletion { watch.cancel() }
        watch.start()
        work.start()
    }
    return !lost
}

/**
 * Runs [block] whenever [condition] holds: started when it turns true, cancelled when it turns
 * false, started again when it turns true once more. A block that completes waits for the next
 * time. Returns only when the flow completes, never for a [Condition].
 */
suspend fun whenever(condition: Flow<Boolean>, block: suspend CoroutineScope.() -> Unit) =
    condition.distinctUntilChanged().collectLatest { if (it) coroutineScope(block) }
