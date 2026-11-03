package dev.versatia

import dev.versatia.event.RegisterListeners
import dev.versatia.resources.ExtractResources
import dev.versatia.task.RunBackgroundTasks
import kotlin.reflect.KClass

/**
 * Marks a `Boolean` parameter of an annotation as a switch: when the parameter is `true` on a
 * plugin's main class or on one of its base classes, the enhancement annotation [enhancement]
 * written on that class or on a class above it is left out of the descriptor. The nearest
 * declaration wins, so a base class may switch an enhancement off and a subclass may declare it
 * again. See docs/enhancements.md.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class VersatiaDisabler(val enhancement: KClass<out Annotation>)

/**
 * Switches off, for the annotated plugin, features that every Versatia plugin otherwise has
 * because `VersatiaPlugin` declares them. Allowed on the main class and on its base classes only.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class DisableVersatiaFeatures(
    /** Leaves the files under `resources/` in the JAR; see `@ExtractResources`. */
    @VersatiaDisabler(ExtractResources::class) val extractResources: Boolean = false,
    /** Leaves the plugin's `Listener`s unregistered; see `@RegisterListeners`. */
    @VersatiaDisabler(RegisterListeners::class) val registerListeners: Boolean = false,
    /**
     * Leaves the plugin's `BackgroundTask`s unstarted; an injected `PluginScope` still works but is
     * then cancelled only when the plugin's beans are closed, after its stop functions. See
     * `@RunBackgroundTasks`.
     */
    @VersatiaDisabler(RunBackgroundTasks::class) val runBackgroundTasks: Boolean = false,
)
