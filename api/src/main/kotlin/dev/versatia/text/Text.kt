package dev.versatia.text

import dev.versatia.VersatiaEngine
import kotlin.reflect.KClass
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.Plugin

/**
 * Turns MiniMessage text with runtime [Argument]s into components: angle brackets are MiniMessage
 * formatting, braces are data. Every plugin gets its own instance by injecting this type: what it
 * registers with [present] and [tag] is visible to that plugin alone and disappears with it.
 * Registering and parsing are safe on any thread, but the built-in presentations read Bukkit state
 * (`{player.location}`, `{world.players}`), which is safe on the main thread only.
 *
 * Besides MiniMessage's tags a text may use the server's styles: names the server maps to
 * MiniMessage in `plugins/VersatiaCore/styles.yml`, shared by every plugin and written as tags,
 * `<positive>Quest {quest} done.`. VersatiaCore defines the names every plugin shares; a plugin
 * ships defaults only for styles of its own, as `styles.yml` next to its `plugin.yml`, and those
 * the server file lacks are added to it when the plugin starts.
 * See https://versatia.dev/docs/messages.
 */
interface Text {
    /** The MiniMessage behind [parse]: the standard tags, nothing else, so no styles either. */
    val miniMessage: MiniMessage

    /**
     * Parses [input], where `{name}` inserts an argument and `{name.property}` one of its
     * properties. A brace naming an argument that was not given, or a property the value does not
     * have, fails the call with a [TextException]; braces around anything else are plain text.
     * A backslash escapes a brace, as MiniMessage's `\<` escapes a tag: `\{nick}` shows `{nick}`,
     * in the text and inside tag arguments, while `\\{nick}` is a backslash and the argument.
     *
     * A tag must exist: one that is neither a MiniMessage tag, a tag registered with [tag], nor a
     * style defined where it stands fails the call with an [UnknownTagException] instead of being
     * shown as text. A style applies the server's MiniMessage to what it encloses; inside another
     * style the definition of the nearest enclosing one wins. A value a brace inserts takes a style
     * by itself: that of its type where the server defines one for a built-in presentation
     * (`player`, `entity`, `item`, `location`, `world`), `accent` otherwise, a [Component] too;
     * what the value formats itself wins over the style. A value that is all the tag of a style
     * holds takes that style alone, and tags holding a value alone win over its own colour,
     * decorations and font, in every part of it: `<!bold>{player.displayName}</!bold>`.
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
     *
     * [style] names the server's style the values are shown in, as [Presentable.style] does for a
     * class of your own: `present<Town>(style = "town")` makes `{town}` look like
     * `<town>{town}</town>`. Without one, and where the server defines no style of that name, a
     * value takes `accent`. A name that cannot be a style fails with an
     * [InvalidStyleNameException].
     */
    fun <T : Any> present(type: KClass<T>, style: String? = null, presenter: (T) -> Presentation)

    /**
     * Adds a MiniMessage tag, `<name:...>`, resolved with the arguments of the call in hand, such as
     * a placeholder bridge. Tags are the one thing in angle brackets that may read arguments. In
     * this plugin's texts the tag wins over a style of the same name.
     */
    fun tag(name: String, tag: ArgumentTag)
}

inline fun <reified T : Any> Text.present(style: String? = null, noinline presenter: (T) -> Presentation) = present(T::class, style, presenter)

/**
 * This plugin's [Text], the instance its beans are injected, so the main class writes
 * `text.present<Duration> { }` with nothing injected. It is there from the moment the plugin's
 * beans exist until the plugin stops; a constructor takes [Text] as a parameter instead.
 */
val Plugin.text get() = VersatiaEngine.instance.defaultBean(this, Text::class)
