package com.github.marcoral.versatia.util.type

/**
 * A secret from the configuration, such as a password or a token. Printed, it shows `****` (`''` when it is
 * empty), so a log line, a message or an exception never carries it; [reveal] hands it to the call that needs it.
 * The `getSecret*` getters of `util.config`, which `@Value` uses, read it from the file, an environment variable
 * (`${env:NAME}`) or another file (`${file:PATH}`).
 */
@JvmInline
value class Secret(private val value: String) {
    /** The secret itself, for the call that needs it; never for a log or a message. */
    fun reveal() = value

    /** Whether the secret is empty, which by convention switches off the feature that needs it. */
    fun isEmpty() = value.isEmpty()

    override fun toString() = if (value.isEmpty()) "''" else "****"

    companion object {
        /** The empty secret, such as an unset password; also the placeholder of a `@Value` property. */
        val EMPTY = Secret("")
    }
}
