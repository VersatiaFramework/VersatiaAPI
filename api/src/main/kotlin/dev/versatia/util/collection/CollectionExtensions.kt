package dev.versatia.util.collection

/**
 * An endless iterator going round this list: its elements in order, then from the first again,
 * such as lines broadcast in turn. The list is copied, so later changes to it are not seen; an
 * empty list is an error, since there would be nothing to go round.
 */
fun <T> List<T>.cycle(): Iterator<T> {
    require(isNotEmpty()) { "Cannot cycle through an empty list" }
    val elements = toList()
    return generateSequence(0) { (it + 1) % elements.size }.map(elements::get).iterator()
}

fun <T> MutableSet<T>.addOrThrow(
    element: T,
    exceptionGenerator: () -> Throwable = { IllegalStateException("Element \"$element\" is already in the set") },
) {
    if (!add(element)) throw exceptionGenerator()
}

/**
 * Adds all [elements] or throws when any of them is already in the set or appears more than once
 * in [elements]. The set is left untouched when the call throws.
 */
fun <T> MutableSet<T>.addAllOrThrow(
    elements: Iterable<T>,
    exceptionGenerator: (duplicates: Collection<T>) -> Throwable = ::defaultAddAllException,
) {
    val snapshot = elements.toList()
    val seen = HashSet<T>()
    val duplicates = LinkedHashSet<T>()
    for (element in snapshot) if (element in this || !seen.add(element)) duplicates += element
    if (duplicates.isNotEmpty()) throw exceptionGenerator(duplicates)
    addAll(snapshot)
}

fun <T> MutableSet<T>.addAllOrThrow(
    elements: Array<out T>,
    exceptionGenerator: (duplicates: Collection<T>) -> Throwable = ::defaultAddAllException,
) = addAllOrThrow(elements.asIterable(), exceptionGenerator)

fun <T> MutableSet<T>.addAllOrThrow(
    elements: Sequence<T>,
    exceptionGenerator: (duplicates: Collection<T>) -> Throwable = ::defaultAddAllException,
) = addAllOrThrow(elements.asIterable(), exceptionGenerator)

private fun defaultAddAllException(duplicates: Collection<*>) = IllegalStateException("Duplicate elements: $duplicates")
