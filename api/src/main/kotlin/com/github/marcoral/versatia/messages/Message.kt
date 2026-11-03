package com.github.marcoral.versatia.messages

import com.github.marcoral.versatia.text.Argument
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component

/**
 * One message: MiniMessage text with [runtime arguments](Argument) in braces, parsed when it is
 * used, never when the file is read, so tags registered later apply. A text without braces and
 * without registered tags is parsed once and then reused. Any failure, such as a brace naming an
 * argument the call did not pass, is a [MessageRenderException] naming the key and the file.
 *
 * A YAML list is a message of several lines, each parsed on its own, so formatting never carries
 * over to the next line; [lines] gives them apart, [get] joined with line breaks.
 */
interface Message {
    /** The dotted key, `shop.bought`. */
    val key: String

    /** The text as written in the file; a YAML list is joined with line breaks. */
    val raw: String

    /** The file the message comes from, relative to the plugin's data folder, with `/` separators. */
    val file: String

    fun get(): Component

    fun get(vararg arguments: Argument): Component

    /**
     * One component per line, one in all for a message that is a single text; for item lore and
     * other places that take lines:
     *
     * ```kotlin
     * meta.lore(messages["sword-lore"].lines(arg("damage", 7)))
     * ```
     */
    fun lines(vararg arguments: Argument): List<Component>

    fun send(audience: Audience, vararg arguments: Argument) = audience.sendMessage(get(*arguments))

    /**
     * Sends to the console and every online player with the permission `bukkit.broadcast.user`, which
     * players have unless a permission plugin revokes it, through `Server.broadcast`; that fires a
     * cancellable `BroadcastMessageEvent`.
     */
    fun broadcast(vararg arguments: Argument)
}
