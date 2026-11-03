package dev.versatia.ksp

import dev.versatia.Phase
import dev.versatia.VersatiaPlugin
import dev.versatia.config.ConfigProcessors
import dev.versatia.di.BeanProcessors
import dev.versatia.engine.descriptor.AnnotationOccurrence
import dev.versatia.engine.descriptor.ClassTarget
import dev.versatia.engine.descriptor.Instantiator
import dev.versatia.engine.descriptor.MemberOwner
import dev.versatia.engine.descriptor.Parameter
import dev.versatia.engine.descriptor.PluginTarget
import dev.versatia.engine.descriptor.PropertyTarget
import dev.versatia.engine.descriptor.VersatiaPluginDescriptor
import dev.versatia.resources.ResourceProcessors
import dev.versatia.task.RunBackgroundTasks
import dev.versatia.task.TaskProcessors
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

            import dev.versatia.VersatiaPlugin
            import dev.versatia.di.LocalBean
            import dev.versatia.task.BackgroundTask

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

            import dev.versatia.VersatiaPlugin
            import dev.versatia.task.BackgroundTask

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

            import dev.versatia.DisableVersatiaFeatures
            import dev.versatia.VersatiaPlugin
            import dev.versatia.task.BackgroundTask

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

            import dev.versatia.Phase
            import dev.versatia.VersatiaPlugin
            import dev.versatia.lifecycle.AutoInvoke

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

            import dev.versatia.ConditionalOnPlugin
            import dev.versatia.VersatiaPlugin
            import dev.versatia.task.BackgroundTask

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
            listOf(AnnotationOccurrence("dev.versatia.ConditionalOnPlugin", mapOf("value" to listOf("Vault")))),
            assertIs<ClassTarget>(task.target).annotations,
        )
    }

    @Test
    fun `a plain task or listener class holds Value properties, set on the instance its enhancement creates`() {
        val result = compile(
            """
            package com.example.alpha

            import dev.versatia.VersatiaPlugin
            import dev.versatia.config.PreviousValueTracking
            import dev.versatia.config.Value
            import dev.versatia.task.BackgroundTask
            import org.bukkit.event.Listener

            class AlphaPlugin : VersatiaPlugin()

            class Broadcasts(@Value("prefix") var prefix: String, @Value("limit") val limit: PreviousValueTracking<Int>) : BackgroundTask {
                @Value("period")
                var period = 0L

                override suspend fun run() {}
            }

            class Greeter : Listener {
                @Value("greeting")
                private var greeting = ""
            }
            """,
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val properties = result.descriptor().elements.map { it.target }.filterIsInstance<PropertyTarget>().associateBy { it.propertyName }
        assertEquals(setOf("prefix", "limit", "period", "greeting"), properties.keys)
        assertTrue(properties.values.all { it.owner == MemberOwner.SUBCLASS_INSTANCE })
        assertEquals(0, properties.getValue("prefix").constructorParameter)
        assertEquals(1, properties.getValue("limit").constructorParameter)
        assertEquals(null, properties.getValue("period").constructorParameter)
        val task = result.descriptor().elementsOf(TaskProcessors.RUN_BACKGROUND_TASKS).single().target
        assertIs<ClassTarget>(task)
        assertTrue(task.constructorParameters.all { it.suppliedBy == ConfigProcessors.VALUE })
    }

    @Test
    fun `a plain task class holds no other enhanced members`() {
        val result = compile(
            """
            package com.example.alpha

            import dev.versatia.Phase
            import dev.versatia.VersatiaPlugin
            import dev.versatia.lifecycle.AutoInvoke
            import dev.versatia.task.BackgroundTask

            class AlphaPlugin : VersatiaPlugin()

            class Broadcasts : BackgroundTask {
                @AutoInvoke(Phase.PLUGIN_START_ONLY)
                fun prepare() {}

                override suspend fun run() {}
            }
            """,
        )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "Broadcasts.prepare cannot be enhanced: Broadcasts is created by a subclass enhancement, which sets only its @Value properties")
    }
}
