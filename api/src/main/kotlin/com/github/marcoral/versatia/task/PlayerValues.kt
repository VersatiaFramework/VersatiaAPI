package com.github.marcoral.versatia.task

import org.bukkit.OfflinePlayer

/**
 * One value per player for state that lives only while the server runs, such as a combat tag, an open menu or a
 * pending request. A player's value goes when they quit, or `keepAfterQuit` later unless they join again meanwhile,
 * and every value goes when the plugin stops; a reload keeps them. Safe from any thread. Made by
 * [PluginScope.perPlayer]; see docs/enhancements.md.
 */
interface PlayerValues<T : Any> {
    operator fun get(player: OfflinePlayer): T?

    operator fun set(player: OfflinePlayer, value: T)

    /** Removes the value of [player] and returns it. */
    fun remove(player: OfflinePlayer): T?

    operator fun contains(player: OfflinePlayer): Boolean

    /** The value of [player], set to [create]'s result first when there is none. */
    fun getOrPut(player: OfflinePlayer, create: () -> T): T
}
