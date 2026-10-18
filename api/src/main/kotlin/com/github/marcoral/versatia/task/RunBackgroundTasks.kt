package com.github.marcoral.versatia.task

import com.github.marcoral.versatia.Phase
import com.github.marcoral.versatia.VersatiaEnhancement
import com.github.marcoral.versatia.VersatiaInternalApi
import com.github.marcoral.versatia.VersatiaSubclassEnhancement

/**
 * Runs every class of the plugin implementing [BackgroundTask] in the plugin's [PluginScope]:
 * started at the tick after the plugin started, cancelled and started again by every reload once
 * the values are set, and cancelled before the plugin's stop functions run, together with every
 * other coroutine of the scope. A task that is a bean runs with its bean instance; any other is
 * created like a bean, with its constructor parameters injected, once per plugin. Declared on
 * `VersatiaPlugin`, so every Versatia plugin has it; see docs/enhancements.md.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@VersatiaSubclassEnhancement(TaskProcessors.RUN_BACKGROUND_TASKS, BackgroundTask::class, Phase.PLUGIN_RELOAD_OR_START)
@VersatiaEnhancement(TaskProcessors.PLUGIN_SCOPE, enhancementPhase = Phase.PLUGIN_START_ONLY)
@VersatiaEnhancement(TaskProcessors.PLUGIN_SCOPE, enhancementPhase = Phase.PLUGIN_STOP)
annotation class RunBackgroundTasks

/** The VersatiaCore processors of this package, by name: the API does not see the runtime. */
@VersatiaInternalApi
object TaskProcessors {
    const val RUN_BACKGROUND_TASKS = "com.github.marcoral.versatia.task.RunBackgroundTasksEnhancementProcessor"

    /** Binds the plugin's `PluginScope` to its lifecycle: resolved at start, cancelled at stop before the stop functions. */
    const val PLUGIN_SCOPE = "com.github.marcoral.versatia.task.PluginScopeEnhancementProcessor"
}
