package com.github.marcoral.versatia.text

import net.kyori.adventure.text.Component

/**
 * How a value appears in text: [component] is what `{name}` inserts, [properties] what
 * `{name.property}` reaches. Every part is evaluated only when a text asks for it, and a property
 * may return anything an argument may be, including another presentable value, so properties
 * chain: `{player.location.x}`.
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

/** A value that knows how it appears in text; takes precedence over any registered presentation. */
fun interface Presentable {
    fun presentation(): Presentation
}
