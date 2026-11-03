package dev.versatia.lifecycle

import dev.versatia.Phase
import dev.versatia.VersatiaEnhancement
import dev.versatia.VersatiaInternalApi

/**
 * Calls the annotated function at [phase]. Parameters are injected like bean dependencies. The
 * function must belong to the plugin's main class, an `object`, or a bean. See docs/enhancements.md.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@VersatiaEnhancement(LifecycleProcessors.AUTO_INVOKE, enhancementPhaseParamRefName = "phase")
annotation class AutoInvoke(val phase: Phase)

/** The VersatiaCore processors of this package, by name: the API does not see the runtime. */
@VersatiaInternalApi
object LifecycleProcessors {
    const val AUTO_INVOKE = "dev.versatia.lifecycle.AutoInvokeEnhancementProcessor"
}
