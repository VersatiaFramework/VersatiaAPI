package dev.versatia.task

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class KeyedJobsTest {
    @Test
    fun `launch cancels the previous job of the key before the new one starts`() = runTest {
        val jobs = backgroundScope.keyedJobs<String>()
        val events = mutableListOf<String>()
        val first = jobs.launch("arena") {
            try {
                awaitCancellation()
            } finally {
                events += "first finally"
            }
        }
        yield()
        val second = jobs.launch("arena") { events += "second body" }
        second.join()
        assertTrue(first.isCancelled)
        assertEquals(listOf("first finally", "second body"), events)
    }

    @Test
    fun `an entry goes when its job completes, never its replacement's`() = runTest {
        val jobs = backgroundScope.keyedJobs<String>()
        val first = jobs.launch("arena") { awaitCancellation() }
        yield()
        val second = jobs.launch("arena") { awaitCancellation() }
        first.join()
        assertTrue("arena" in jobs, "the replacement survives the completion of the job it replaced")
        second.cancel()
        second.join()
        assertFalse("arena" in jobs)

        jobs.launch("quick") {}.join()
        assertEquals(emptySet(), jobs.keys)
    }

    @Test
    fun `contains, cancel and cancelAll`() = runTest {
        val jobs = backgroundScope.keyedJobs<String>()
        jobs.launch("a") { awaitCancellation() }
        jobs.launch("b") { awaitCancellation() }
        jobs.launch("c") { awaitCancellation() }
        assertTrue("a" in jobs)
        assertFalse("missing" in jobs)
        assertTrue(jobs.cancel("a"))
        assertFalse(jobs.cancel("a"))
        assertFalse(jobs.cancel("missing"))
        assertFalse("a" in jobs)
        assertEquals(setOf("b", "c"), jobs.keys)
        jobs.cancelAll()
        assertEquals(emptySet(), jobs.keys)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a body completing synchronously leaves no entry`() = runTest(UnconfinedTestDispatcher()) {
        val jobs = keyedJobs<String>()
        var ran = false
        val job = jobs.launch("arena") { ran = true }
        assertTrue(ran)
        assertTrue(job.isCompleted)
        assertEquals(emptySet(), jobs.keys)
    }

    @Test
    fun `a launch on a cancelled scope leaves no entry`() = runTest {
        val jobs = CoroutineScope(Job().apply { cancel() }).keyedJobs<String>()
        var ran = false
        val job = jobs.launch("arena") { ran = true }
        job.join()
        assertFalse(ran)
        assertTrue(job.isCancelled)
        assertEquals(emptySet(), jobs.keys)
    }

    @Test
    fun `concurrent launches end with every job completed and no entry`() = runTest {
        val jobs = CoroutineScope(Dispatchers.Default).keyedJobs<Int>()
        val launched = ConcurrentLinkedQueue<Job>()
        withContext(Dispatchers.Default) {
            repeat(8) { worker ->
                launch { repeat(500) { launched += jobs.launch((worker * 500 + it) % 37) { yield() } } }
            }
        }
        launched.joinAll()
        assertEquals(4000, launched.size)
        assertTrue(launched.all { it.isCompleted })
        assertEquals(emptySet(), jobs.keys)
    }
}
