package com.github.marcoral.versatia.text

import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue

/**
 * A tag registered with [Text.tag]: resolved like any MiniMessage tag, with the arguments of the
 * call in hand, so `<papi:player:kills>` can look up the `player` argument.
 */
fun interface ArgumentTag {
    fun resolve(arguments: ArgumentQueue, context: Context, values: Arguments): Tag?
}
