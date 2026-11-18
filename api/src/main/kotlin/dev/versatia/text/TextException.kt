package dev.versatia.text

import kotlin.reflect.KClass

open class TextException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class InvalidArgumentNameException(name: String) :
    TextException("\"$name\" cannot name an argument: letters, digits, _ and - only")

class DuplicateArgumentException(name: String) :
    TextException("Argument \"$name\" is given twice")

/** A [Presentable] or a presentation registered with [Text.present] names a style that no server can define. */
class InvalidStyleNameException(name: String, type: KClass<*>, reason: String) :
    TextException("\"$name\" cannot name the style of ${type.qualifiedName ?: type}: $reason")

class ReservedTagNameException(name: String) :
    TextException("\"$name\" cannot name a tag: it is a MiniMessage tag, reserved for arguments (versatia-argument-...), or not a tag name (letters, digits, _ and -)")

/**
 * A text uses a tag that does not exist where it stands: it is neither a MiniMessage tag, a tag
 * registered with [Text.tag], nor a style the server defines there. Such a tag is never shown as
 * text; a backslash keeps the brackets, `\<player>`.
 */
class UnknownTagException private constructor(message: String) : TextException(message) {
    /** [tags] exist nowhere; [styles] are the styles the server defines in [source]. */
    constructor(tags: Collection<String>, styles: Collection<String>, source: String) : this(
        tags.joinToString { "<$it>" } + (if (tags.size == 1) " is neither a MiniMessage tag nor a style" else " are neither MiniMessage tags nor styles") +
            (if (styles.isEmpty()) "; $source defines no styles" else "; the styles in $source are: ${styles.joinToString()}") +
            ". Write \\<${tags.first()}> to show ${if (tags.size == 1) "it" else "one"} as text",
    )

    /** [style] is a style only inside the tags [scopes], and the text uses it outside them. */
    constructor(style: String, scopes: Collection<String>, source: String) : this(
        "<$style> is a style only inside ${scopes.joinToString { "<$it>" }}; define it at the top level of $source to use it anywhere",
    )
}

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
