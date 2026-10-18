package com.github.marcoral.versatia.task

import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Jobs of a scope addressed by key, at most one per key: a combat tag per player, a countdown per arena. Thread-safe. */
class KeyedJobs<K : Any> internal constructor(private val scope: CoroutineScope) {
    private val jobs = ConcurrentHashMap<K, Job>()

    /** The keys whose job has not completed yet. */
    val keys: Set<K> get() = jobs.keys

    operator fun contains(key: K) = jobs[key]?.isActive == true

    /** Cancels the job of [key], if any, then launches [block] as its new job; the entry goes when that job completes. */
    fun launch(key: K, context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit) =
        scope.launch(context, CoroutineStart.LAZY, block).also { job ->
            jobs.put(key, job)?.cancel()
            job.invokeOnCompletion { jobs.remove(key, job) }
            job.start()
        }

    /** Cancels the job of [key]; true when one was running. */
    fun cancel(key: K) = jobs.remove(key)?.let { job -> job.isActive.also { job.cancel() } } == true

    fun cancelAll() = jobs.keys.toList().forEach(::cancel)
}

/** Jobs of this scope addressed by key, at most one per key; see [KeyedJobs]. */
fun <K : Any> CoroutineScope.keyedJobs() = KeyedJobs<K>(this)
