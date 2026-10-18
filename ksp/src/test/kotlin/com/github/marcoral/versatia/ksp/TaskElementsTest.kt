package com.github.marcoral.versatia.ksp

import com.github.marcoral.versatia.Phase
import com.github.marcoral.versatia.VersatiaPlugin
import com.github.marcoral.versatia.di.BeanProcessors
import com.github.marcoral.versatia.engine.descriptor.AnnotationOccurrence
import com.github.marcoral.versatia.engine.descriptor.ClassTarget
import com.github.marcoral.versatia.engine.descriptor.Instantiator
import com.github.marcoral.versatia.engine.descriptor.Parameter
import com.github.marcoral.versatia.engine.descriptor.PluginTarget
import com.github.marcoral.versatia.engine.descriptor.VersatiaPluginDescriptor
import com.github.marcoral.versatia.resources.ResourceProcessors
import com.github.marcoral.versatia.task.RunBackgroundTasks
import com.github.marcoral.versatia.task.TaskProcessors
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/** The elements `@RunBackgroundTasks` on `VersatiaPlugin` produces; see docs/enhancements.md. */
@OptIn(ExperimentalCompilerApi::class)
class TaskElementsTest : ProcessorTest() {
    private fun compile(source: String) = compile(SourceFile.kotlin("Plugin.kt", source), mainClass = null)

    private fun VersatiaPluginDescriptor.elementsOf(processorClass: String) = elements.filter { it.processorClass == processorClass }

    @Test
    fun `every BackgroundTask becomes an element, and the plugin gets two scope elements`() {
        val result = compile(
            """
            package com.example.alpha

            import com.github.marcoral.versatia.VersatiaPlugin
            import com.github.marcoral.versatia.di.LocalBean
            import com.github.marcoral.versatia.task.BackgroundTask

            class AlphaPlugin : VersatiaPlugin()

            @LocalBean
            class Clock

            object Heartbeat : BackgroundTask {
                override suspend fun run() {}
            }

            class Autosave(val clock: Clock) : BackgroundTask {
                override suspend fun run() {}
            }
            """,
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val descriptor = result.descriptor()
        val tasks = descriptor.elementsOf(TaskProcessors.RUN_BACKGROUND_TASKS)
        assertEquals(listOf("com.example.alpha.Autosave", "com.example.alpha.Heartbeat"), tasks.map { it.target.className })
        assertTrue(tasks.all { it.phase == Phase.PLUGIN_RELOAD_OR_START })
        assertTrue(tasks.all { it.origin == VersatiaPlugin::class.java.name })
        assertTrue(tasks.all { it.annotation == AnnotationOccurrence(RunBackgroundTasks::class.java.name) })
        assertEquals(listOf(Parameter("com.example.alpha.Clock")), assertIs<ClassTarget>(tasks[0].target).constructorParameters)
        assertIs<Instantiator.Direct>(assertIs<ClassTarget>(tasks[1].target).instantiator)

        val scope = descriptor.elementsOf(TaskProcessors.PLUGIN_SCOPE)
        assertEquals(listOf(Phase.PLUGIN_START_ONLY, Phase.PLUGIN_STOP), scope.map { it.phase })
        assertEquals(listOf("com.example.alpha.AlphaPlugin", "com.example.alpha.AlphaPlugin"), scope.map { assertIs<PluginTarget>(it.target).className })
        assertTrue(scope.all { it.origin == VersatiaPlugin::class.java.name })
    }

    @Test
    fun `a main class implementing BackgroundTask is enhanced as the plugin`() {
        val result = compile(
            """
            package com.example.alpha

            import com.github.marcoral.versatia.VersatiaPlugin
            import com.github.marcoral.versatia.task.BackgroundTask

            class AlphaPlugin : VersatiaPlugin(), BackgroundTask {
                override suspend fun run() {}
            }
            """,
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val task = result.descriptor().elementsOf(TaskProcessors.RUN_BACKGROUND_TASKS).single()
        assertEquals("com.example.alpha.AlphaPlugin", assertIs<PluginTarget>(task.target).className)
    }

    @Test
    fun `the switch removes the tasks and the scope elements`() {
        val result = compile(
            """
            package com.example.alpha

            import com.github.marcoral.versatia.DisableVersatiaFeatures
            import com.github.marcoral.versatia.VersatiaPlugin
            import com.github.marcoral.versatia.task.BackgroundTask

            @DisableVersatiaFeatures(runBackgroundTasks = true)
            class AlphaPlugin : VersatiaPlugin()

            object Heartbeat : BackgroundTask {
                override suspend fun run() {}
            }
            """,
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertEquals(listOf(BeanProcessors.PLUGIN, ResourceProcessors.EXTRACT), result.descriptor().elements.map { it.processorClass })
    }

    @Test
    fun `rejects a suspend enhanced function and points to the alternatives`() {
        val result = compile(
            """
            package com.example.alpha

            import com.github.marcoral.versatia.Phase
            import com.github.marcoral.versatia.VersatiaPlugin
            import com.github.marcoral.versatia.lifecycle.AutoInvoke

            class AlphaPlugin : VersatiaPlugin() {
                @AutoInvoke(Phase.PLUGIN_START_ONLY)
                suspend fun warmUp() {}
            }
            """,
        )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(
            result.messages,
            "AlphaPlugin.warmUp must not be suspend to be enhanced; implement BackgroundTask for work that runs with the plugin, or launch from an injected PluginScope",
        )
    }

    @Test
    fun `records ConditionalOnPlugin on a task class`() {
        val result = compile(
            """
            package com.example.alpha

            import com.github.marcoral.versatia.ConditionalOnPlugin
            import com.github.marcoral.versatia.VersatiaPlugin
            import com.github.marcoral.versatia.task.BackgroundTask

            class AlphaPlugin : VersatiaPlugin()

            @ConditionalOnPlugin("Vault")
            object Payouts : BackgroundTask {
                override suspend fun run() {}
            }
            """,
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val task = result.descriptor().elementsOf(TaskProcessors.RUN_BACKGROUND_TASKS).single()
        assertEquals(
            listOf(AnnotationOccurrence("com.github.marcoral.versatia.ConditionalOnPlugin", mapOf("value" to listOf("Vault")))),
            assertIs<ClassTarget>(task.target).annotations,
        )
    }

    @Test
    fun `a plain task class cannot hold Value properties`() {
        val result = compile(
            """
            package com.example.alpha

            import com.github.marcoral.versatia.VersatiaPlugin
            import com.github.marcoral.versatia.config.Value
            import com.github.marcoral.versatia.task.BackgroundTask

            class AlphaPlugin : VersatiaPlugin()

            class Broadcasts : BackgroundTask {
                @Value("period")
                var period = 0L

                override suspend fun run() {}
            }
            """,
        )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "Broadcasts.period cannot be enhanced: Broadcasts is neither the plugin's main class, an object nor a bean")
    }
}
