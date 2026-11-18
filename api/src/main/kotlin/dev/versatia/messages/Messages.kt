package dev.versatia.messages

import dev.versatia.VersatiaEngine
import org.bukkit.plugin.Plugin

/**
 * The messages of one plugin, read from the YAML files under `messages/` in its data folder and
 * reloaded with it. Every plugin gets its own instance by injecting this type. A file whose name
 * starts with a dot holds no messages; `messages/.versatia.yml` holds settings. Each load logs a
 * warning through the plugin's logger for every message with legacy colour codes (`&a`, `§l`),
 * naming the MiniMessage tags to use instead, unless that file sets `legacy-color-warnings: false`.
 *
 * Every tag of every message must exist, as a MiniMessage tag, a tag registered on the plugin's
 * `Text` or a style of the server: the bank is checked once the plugin has started, so tags its
 * start functions register count, and at every reload when the files are read. A tag that does
 * not exist fails the start or the reload with a [MessagesLoadException] naming the file, the key
 * and the styles.
 * See https://versatia.dev/docs/messages.
 */
interface Messages {
    /** The keys of every message, in file order. */
    val keys: Set<String>

    operator fun contains(key: String): Boolean

    /** The message at [key]; throws [MissingMessageException] when there is none. */
    operator fun get(key: String): Message

    fun getOrNull(key: String): Message?
}

/**
 * This plugin's [Messages], the instance its beans are injected, so the main class writes
 * `messages["shop.open"]` with nothing injected. It is there from the moment the plugin's beans
 * exist until the plugin stops; a constructor takes [Messages] as a parameter instead.
 */
val Plugin.messages get() = VersatiaEngine.instance.defaultBean(this, Messages::class)
