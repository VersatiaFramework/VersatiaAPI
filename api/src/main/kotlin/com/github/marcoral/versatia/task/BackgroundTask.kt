package com.github.marcoral.versatia.task

/**
 * Work that runs as long as its plugin, found at compile time, never registered. [run] is called
 * in the plugin's [PluginScope] on the server thread, beginning at the tick after the plugin
 * started or reloaded; a reload cancels it once the new values are set and calls it again, and
 * the plugin's stop cancels it before the stop functions run. Returning ends the task until the
 * next reload; throwing is logged with the class name and ends it as well. See docs/enhancements.md.
 */
interface BackgroundTask {
    suspend fun run()
}
