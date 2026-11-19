package dev.versatia.text

import net.kyori.adventure.text.Component

/**
 * How a value appears in text: [component] is what `{name}` inserts, [properties] what
 * `{name.property}` reaches. Every part is evaluated only when a text asks for it, and a property
 * may return anything an argument may be, including another presentable value, so properties
 * chain: `{player.location.x}`. The component stands out by itself wherever a text inserts it,
 * in the style [Presentable.style] or the registration names, `accent` otherwise; what it formats
 * itself wins over that style.
 *
 * Several presentations may apply to one value: its own, those of the server and of the plugin
 * parsing the text, and the built-in ones, in that order and the nearest type first. The component,
 * the style and every property come from the first of them that defines it, so a presentation may
 * define one part alone and leave the rest as they were: a component of its own for a player still
 * takes the style `player`, unless the presentation names another.
 */
class Presentation internal constructor(
    val component: (() -> Component)?,
    val properties: Map<String, () -> Any?>,
)

class PresentationBuilder internal constructor() {
    private var component: (() -> Component)? = null
    private val properties = LinkedHashMap<String, () -> Any?>()

    /** What the bare tag inserts; without it the value can only be used through its properties. */
    fun component(component: () -> Component) {
        this.component = component
    }

    fun property(name: String, value: () -> Any?) {
        require(NAME.matches(name)) { "\"$name\" cannot name a property: letters, digits, _ and - only" }
        properties[name] = value
    }

    internal fun build() = Presentation(component, properties)
}

fun presentation(build: PresentationBuilder.() -> Unit) = PresentationBuilder().apply(build).build()

/**
 * How the values of [T] appear in the messages of every plugin, as a class annotated with
 * [ServerPresentation]: `class PlayerCardPresenter : Presenter<Player>`. [present] runs whenever
 * a text of any plugin shows such a value, on any thread, the async chat event's included, so it
 * must be cheap and safe to call from several threads at once; so must the parts of what it
 * returns.
 */
fun interface Presenter<T : Any> {
    fun present(value: T): Presentation
}

/** A value that knows how it appears in text; takes precedence over any registered presentation. */
fun interface Presentable {
    fun presentation(): Presentation

    /**
     * The server's style this value is shown in, as `player` is the style of a player: a class
     * answering `"quest"` makes `{quest}` look like `<quest>{quest}</quest>`. Without one the value
     * takes the style a presentation of its type names, and `accent` where none names one or the
     * server defines no style of that name. A name that cannot be a style fails with an
     * [InvalidStyleNameException] when the value is shown.
     */
    val style: String? get() = null
}
