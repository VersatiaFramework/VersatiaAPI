package com.github.marcoral.versatia.text

/**
 * A runtime argument of a text: `{name}` inserts [value] and `{name.property}` one of its
 * properties, as its [Presentation] defines them. Strings are inserted verbatim, never parsed, so
 * player input cannot smuggle tags in; a `Component` is inserted as it is. Names consist of
 * letters, digits, `_` and `-`; any other name throws [InvalidArgumentNameException] here.
 */
class Argument(val name: String, val value: Any?) {
    init {
        if (!NAME.matches(name)) throw InvalidArgumentNameException(name)
    }

    override fun toString() = "$name = $value"
}

fun arg(name: String, value: Any?) = Argument(name, value)

/** The names of arguments and properties. */
internal val NAME = Regex("[A-Za-z0-9_-]+")
