package com.github.marcoral.versatia.text

import kotlin.reflect.KClass

open class TextException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class InvalidArgumentNameException(name: String) :
    TextException("\"$name\" cannot name an argument: letters, digits, _ and - only")

class DuplicateArgumentException(name: String) :
    TextException("Argument \"$name\" is given twice")

class ReservedTagNameException(name: String) :
    TextException("\"$name\" cannot name a tag: it is a MiniMessage tag, reserved for arguments (versatia-argument-...), or not a tag name (lower-case letters, digits, _ and -)")

class UnknownArgumentException(name: String, available: Set<String>) :
    TextException("No argument \"$name\"; the arguments are: ${available.listed()}")

class UnknownPropertyException(path: String, property: String, available: Set<String>) :
    TextException("{$path} has no property \"$property\"; ${if (available.isEmpty()) "it has none" else "it has: ${available.listed()}"}")

class NullValueException(path: String, property: String) :
    TextException("{$path} is null, so it has no property \"$property\"")

class NoTextFormException(path: String, available: Set<String>) :
    TextException("{$path} cannot be inserted as text; use one of its properties: ${available.listed()}")

/** A presentation, or the code it calls, threw while showing the brace; [cause] is what it threw. */
class PresentationException(path: String, cause: RuntimeException) :
    TextException("{$path} cannot be shown: $cause", cause)

class ArgumentTypeException(name: String, expected: KClass<*>, actual: Any?) :
    TextException("Argument \"$name\" is ${if (actual == null) "null" else "a ${actual.javaClass.name}"}, expected ${expected.qualifiedName}")

private fun Set<String>.listed() = if (isEmpty()) "(none)" else joinToString()
