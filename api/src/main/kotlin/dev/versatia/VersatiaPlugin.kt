package dev.versatia

import dev.versatia.di.PluginBean
import dev.versatia.event.RegisterListeners
import dev.versatia.resources.ExtractResources
import dev.versatia.task.RunBackgroundTasks
import org.bukkit.plugin.java.JavaPlugin

/**
 * Base class of Versatia plugins. There is nothing to override: declare beans and mark functions
 * with `@AutoInvoke` instead. The enhancements declared here apply to every plugin extending the
 * class, as they would to any base class of a main class; see docs/enhancements.md.
 */
@PluginBean
@ExtractResources
@RegisterListeners
@RunBackgroundTasks
abstract class VersatiaPlugin : JavaPlugin() {
    /** Runs this plugin's reload-phase enhancements, and then those of the plugins using its beans, as `/versatia reload <name>` does. */
    fun versatiaReload() = VersatiaEngine.instance.reload(this)

    final override fun onEnable() = VersatiaEngine.instance.enhance(this)

    // Dependents are disabled first, so the engine is missing only when VersatiaCore never ran, which onEnable reported
    final override fun onDisable() {
        VersatiaEngine.loadServiceOrNull<VersatiaEngine>()?.disable(this)
    }
}
