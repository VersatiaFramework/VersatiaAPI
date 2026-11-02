package com.github.marcoral.versatia.util.config

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.Date
import org.bukkit.configuration.ConfigurationSection

/**
 * The text of a configured secret: `${env:NAME}` becomes the environment variable, `${file:PATH}` the content of the
 * file (UTF-8 without a byte order mark and trailing line breaks; a relative path starts at the server's working
 * directory, an absolute one such as `/etc/minecraft/token.txt` is taken as it is) and `${pluginfile:PATH}` the content
 * of a file in the data folder of the plugin that reads it, and what they hold is taken as written. Any other `${...}` is an error, and so is a value YAML read as
 * a number, a boolean or a date, which may differ from what was written. No message shows the secret. See docs/api.md.
 */
internal object SecretReferences {
    private const val EXPECTED = "a secret: text, \${env:NAME}, \${file:PATH} or \${pluginfile:PATH}"
    private const val BYTE_ORDER_MARK = "﻿"
    private val REFERENCE = Regex("""\$\{([^}]*)}""")
    private val VARIABLE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /**
     * The text of [raw] with its references replaced; a message starts with [where] (`Value at "db.password"`), unless [message] replaces it.
     * [dataFolder] is where `${pluginfile:PATH}` starts; null when the value was not read for a plugin.
     */
    fun resolve(raw: Any?, where: String, message: String?, dataFolder: Path? = null): String {
        fun fail(problem: String): Nothing = throw ConfigurationValueException(message ?: "$where $problem")
        val text = when (raw) {
            is String -> raw
            null -> fail("is empty, expected $EXPECTED")
            is ConfigurationSection, is Map<*, *> -> fail("is a section, expected $EXPECTED")
            is Collection<*> -> fail("is a list, expected $EXPECTED")
            else -> fail("is read as ${kindOf(raw)}, not text; put the secret in quotes, since YAML changes unquoted numbers, booleans and dates (0123 reads as 83)")
        }
        return REFERENCE.replace(text) { reference(it.value, it.groupValues[1], dataFolder, ::fail) }
    }

    private fun reference(written: String, content: String, dataFolder: Path?, fail: (String) -> Nothing): String {
        val kind = content.substringBefore(':', "").trim()
        val target = content.substringAfter(':', "").trim()
        return when {
            kind.equals("env", ignoreCase = true) -> variable(written, target, fail)
            kind.equals("file", ignoreCase = true) -> file(written, target, null, fail)
            kind.equals("pluginfile", ignoreCase = true) -> file(written, target, dataFolder ?: fail(noPluginFolder(written, target)), fail)
            VARIABLE_NAME.matches(content.trim()) -> fail("uses $written, which is none of \${env:NAME}, \${file:PATH} and \${pluginfile:PATH}; did you mean \${env:${content.trim()}}?")
            else -> fail("uses $written, which is none of \${env:NAME}, \${file:PATH} and \${pluginfile:PATH}; a secret that contains \${ goes into a file or a variable, whose content is taken as written")
        }
    }

    private fun noPluginFolder(written: String, target: String) =
        "uses $written, but this file is not read for a plugin, so there is no plugin folder to start at; write \${file:plugins/<plugin>/$target} instead"

    private fun variable(written: String, name: String, fail: (String) -> Nothing): String {
        if (name.isEmpty()) fail("uses $written, which names no variable")
        return System.getenv(name) ?: fail("uses $written, which is not set; set that environment variable for the server, or write the value instead")
    }

    /** The file at [target], relative to [base] or, without one, to the working directory. */
    private fun file(written: String, target: String, base: Path?, fail: (String) -> Nothing): String {
        if (target.isEmpty()) fail("uses $written, which names no file")
        val path = try {
            (base?.resolve(target) ?: Path.of(target)).toAbsolutePath().normalize()
        } catch (e: InvalidPathException) {
            fail("uses $written, which is not a valid path: ${e.reason}")
        }
        if (!Files.isRegularFile(path)) fail("uses $written, but $path ${if (Files.exists(path)) "is not a file" else "does not exist"}")
        val bytes = try {
            Files.readAllBytes(path)
        } catch (e: IOException) {
            fail("uses $written, but $path cannot be read (${e.javaClass.simpleName})")
        }
        val content = try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            fail("uses $written, but $path is not UTF-8 text; save it as UTF-8")
        }
        return content.removePrefix(BYTE_ORDER_MARK).trimEnd('\r', '\n')
    }

    private fun kindOf(raw: Any) = when (raw) {
        is Boolean -> "a boolean"
        is Number -> "a number"
        is Date -> "a date"
        else -> "a ${raw.javaClass.simpleName}"
    }
}
