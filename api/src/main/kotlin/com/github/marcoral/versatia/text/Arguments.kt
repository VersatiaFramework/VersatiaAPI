package com.github.marcoral.versatia.text

/** The arguments of one [Text.parse] call, as a tag sees them. */
interface Arguments {
    val names: Set<String>

    operator fun contains(name: String): Boolean

    /** The value of the argument [name]; throws [UnknownArgumentException] when there is none. */
    operator fun get(name: String): Any?
}

/** The argument [name] as a [T]; throws when it is missing or of another type. */
inline fun <reified T : Any> Arguments.require(name: String): T = this[name] as? T ?: throw ArgumentTypeException(name, T::class, this[name])
