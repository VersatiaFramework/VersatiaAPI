package dev.versatia.text

import kotlin.reflect.KClass
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

/**
 * Turns MiniMessage text with runtime [Argument]s into components: angle brackets are MiniMessage
 * formatting, braces are data. Every plugin gets its own instance by injecting this type: what it
 * registers with [present] and [tag] is visible to that plugin alone and disappears with it.
 * Registering and parsing are safe on any thread, but the built-in presentations read Bukkit state
 * (`{player.location}`, `{world.players}`), which is safe on the main thread only.
 * See https://versatia.dev/docs/messages.
 */
interface Text {
    /** The MiniMessage behind [parse]: the standard tags, nothing else. */
    val miniMessage: MiniMessage

    /**
     * Parses [input], where `{name}` inserts an argument and `{name.property}` one of its
     * properties. A brace naming an argument that was not given, or a property the value does not
     * have, fails the call with a [TextException]; braces around anything else are plain text.
     * A backslash escapes a brace, as MiniMessage's `\<` escapes a tag: `\{nick}` shows `{nick}`,
     * in the text and inside tag arguments, while `\\{nick}` is a backslash and the argument.
     *
     * In the text a brace inserts the value's component. Inside an argument of a tag, where
     * MiniMessage reads a plain string, `<click:suggest_command:'/msg {player.name} '>`,
     * `<insert:{player.name}>`, `<color:{c}>`, it inserts the value's plain text, escaped for the
     * argument's quotes, so a value can neither end the argument nor open a tag; an unquoted
     * argument holding a brace is quoted. The text of `<hover:show_text:...>`, which MiniMessage
     * parses again, keeps the component.
     *
     * A text without braces and without tags registered with [tag] gives the same component every
     * time, so it is parsed once and reused until a tag is registered.
     */
    fun parse(input: String, vararg arguments: Argument): Component

    /** The names of the arguments [input] refers to, escaped braces aside, so a caller can tell whether it needs any. */
    fun argumentNames(input: String): Set<String>

    /**
     * Teaches this instance how values of [type] appear in text. A value that is [Presentable]
     * describes itself and wins; otherwise the registered type nearest to the value's class is
     * used, the built-in ones last; this holds for strings and numbers too. Registering a
     * type again replaces its presentation.
     */
    fun <T : Any> present(type: KClass<T>, presenter: (T) -> Presentation)

    /**
     * Adds a MiniMessage tag, `<name:...>`, resolved with the arguments of the call in hand, such as
     * a placeholder bridge. Tags are the one thing in angle brackets that may read arguments.
     */
    fun tag(name: String, tag: ArgumentTag)
}

inline fun <reified T : Any> Text.present(noinline presenter: (T) -> Presentation) = present(T::class, presenter)
