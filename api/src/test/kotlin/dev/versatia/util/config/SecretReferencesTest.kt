package dev.versatia.util.config

import dev.versatia.util.type.Secret
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.io.TempDir

/** `VERSATIA_TEST_SECRET` is set by the test task in build.gradle.kts. */
class SecretReferencesTest {
    @TempDir
    lateinit var folder: File

    private fun yaml(content: String) = YamlConfiguration().apply { loadFromString(content) }

    private fun failure(content: String, key: String = "password") =
        assertFailsWith<ConfigurationValueException> { yaml(content).getSecretOrNull(key) }.message.orEmpty()

    private fun file(name: String, text: String) = folder.resolve(name).apply { writeText(text) }.absolutePath.replace('\\', '/')

    @Test
    fun `reads text, environment variables and files`() {
        val token = file("token.txt", "﻿from-a-file\r\n\n")
        val config = yaml(
            """
            plain: hunter2
            quoted: '0123'
            env: ${'$'}{env:VERSATIA_TEST_SECRET}
            combined: Bot ${'$'}{env:VERSATIA_TEST_SECRET}/${'$'}{file:$token}
            upper: '${'$'}{ENV: VERSATIA_TEST_SECRET }'
            file: ${'$'}{file:$token}
            """.trimIndent(),
        )
        assertEquals("hunter2", config.getSecretOrNull("plain")?.reveal())
        assertEquals("0123", config.getSecretOrThrow("quoted").reveal())
        assertEquals("from-the-environment", config.getSecretOrNull("env")?.reveal())
        assertEquals("Bot from-the-environment/from-a-file", config.getSecretOrNull("combined")?.reveal())
        assertEquals("from-the-environment", config.getSecretOrNull("upper")?.reveal(), "the kind ignores case and spaces are trimmed")
        assertEquals("from-a-file", config.getSecretOrNull("file")?.reveal(), "the byte order mark and the trailing line breaks are dropped")
        assertNull(config.getSecretOrNull("missing"))
        assertEquals(Secret.EMPTY, config.getSecret("missing", Secret.EMPTY))
        assertTrue(config.isSecret("env"))
    }

    @Test
    fun `what a variable or a file holds is taken as written`() {
        val nested = file("nested.txt", "\${env:VERSATIA_TEST_SECRET}\nsecond line\n")
        assertEquals("\${env:VERSATIA_TEST_SECRET}\nsecond line", yaml("password: \${file:$nested}").getSecretOrNull("password")?.reveal())
    }

    @Test
    fun `a relative file path starts at the working directory`() {
        val relative = Path.of("build", "tmp", "secret-references-test.txt")
        relative.parent.createDirectories()
        relative.writeText("relative\n")
        try {
            assertEquals("relative", yaml("password: \${file:build/tmp/secret-references-test.txt}").getSecretOrNull("password")?.reveal())
        } finally {
            relative.deleteIfExists()
        }
    }

    @Test
    fun `an absolute path starting with a slash is taken as it is`() {
        val absolute = file("absolute.txt", "absolute").substringAfter(':').takeIf { it.startsWith("/") } ?: file("absolute.txt", "absolute")
        assertEquals("absolute", yaml("password: \${file:$absolute}").getSecretOrNull("password")?.reveal())
    }

    @Test
    fun `pluginfile starts at the data folder of the plugin that reads the file`() {
        val dataFolder = folder.resolve("plugins/Shop").apply { mkdirs() }
        dataFolder.resolve("api-key.txt").writeText("from-the-plugin-folder\n")
        val config = folder.resolve("config.yml").apply { writeText("password: \${pluginfile:api-key.txt}\nmissing: \${pluginfile:nope.txt}") }
        val loaded = loadYamlOrThrow(config, dataFolder)
        assertEquals("from-the-plugin-folder", loaded.getSecretOrNull("password")?.reveal())
        val missing = assertFailsWith<ConfigurationValueException> { loaded.getSecretOrNull("missing") }.message.orEmpty()
        assertTrue(missing.startsWith("Value at \"missing\" uses \${pluginfile:nope.txt}, but ") && missing.endsWith("nope.txt does not exist"), missing)
    }

    @Test
    fun `pluginfile outside a plugin's file says what to write instead`() {
        assertEquals(
            "Value at \"password\" uses \${pluginfile:api-key.txt}, but this file is not read for a plugin, so there is no plugin folder to start at; " +
                "write \${file:plugins/<plugin>/api-key.txt} instead",
            failure("password: \${pluginfile:api-key.txt}"),
        )
    }

    @Test
    fun `a reference that cannot be resolved names the key and the reason`() {
        assertEquals(
            "Value at \"db.password\" uses \${env:VERSATIA_TEST_UNSET}, which is not set; set that environment variable for the server, or write the value instead",
            failure("db:\n  password: \${env:VERSATIA_TEST_UNSET}", "db.password"),
        )
        val missing = folder.resolve("missing.txt").absoluteFile.toPath().normalize()
        assertEquals(
            "Value at \"password\" uses \${file:${folder.absolutePath.replace('\\', '/')}/missing.txt}, but $missing does not exist",
            failure("password: \${file:${folder.absolutePath.replace('\\', '/')}/missing.txt}"),
        )
        assertTrue(failure("password: \${file:${folder.absolutePath.replace('\\', '/')}}").endsWith("is not a file"))
        folder.resolve("latin1.txt").toPath().writeBytes(byteArrayOf(0x70, 0xE9.toByte(), 0x0A))
        assertTrue(failure("password: \${file:${folder.absolutePath.replace('\\', '/')}/latin1.txt}").endsWith("is not UTF-8 text; save it as UTF-8"))
        assertEquals("Value at \"password\" uses \${env:}, which names no variable", failure("password: \${env:}"))
        assertEquals("Value at \"password\" uses \${file: }, which names no file", failure("password: '\${file: }'"))
        assertFalse(yaml("password: \${env:VERSATIA_TEST_UNSET}").isSecret("password"))
    }

    @Test
    fun `any other reference is an error`() {
        assertEquals(
            "Value at \"password\" uses \${DB_PASSWORD}, which is none of \${env:NAME}, \${file:PATH} and \${pluginfile:PATH}; did you mean \${env:DB_PASSWORD}?",
            failure("password: \${DB_PASSWORD}"),
        )
        assertTrue(failure("password: ab\${vault:db}").endsWith("a secret that contains \${ goes into a file or a variable, whose content is taken as written"))
        assertEquals("ab\${cd", yaml("password: ab\${cd").getSecretOrNull("password")?.reveal(), "an unclosed \${ is text")
    }

    @Test
    fun `a value YAML did not read as text is rejected without showing it`() {
        val number = failure("password: 4567")
        assertTrue(number.startsWith("Value at \"password\" is read as a number, not text; put the secret in quotes"), number)
        assertFalse("4567" in number, number)
        assertTrue(failure("password: true").startsWith("Value at \"password\" is read as a boolean, not text"))
        assertEquals("Value at \"password\" is a section, expected a secret: text, \${env:NAME}, \${file:PATH} or \${pluginfile:PATH}", failure("password:\n  a: hidden"))
        val list = failure("password: [hidden]")
        assertEquals("Value at \"password\" is a list, expected a secret: text, \${env:NAME}, \${file:PATH} or \${pluginfile:PATH}", list)
    }

    @Test
    fun `OrThrow fails for a missing key and takes a message of its own`() {
        val config = yaml("password: \${env:VERSATIA_TEST_UNSET}")
        assertEquals("No value present at \"token\"", assertFailsWith<ConfigurationValueException> { config.getSecretOrThrow("token") }.message)
        assertEquals("Set the password", assertFailsWith<ConfigurationValueException> { config.getSecretOrThrow("password", "Set the password") }.message)
    }

    @Test
    fun `lists resolve every element and name the one that fails`() {
        val config = yaml(
            """
            tokens: [plain, '${'$'}{env:VERSATIA_TEST_SECRET}']
            broken: [plain, '${'$'}{env:VERSATIA_TEST_UNSET}']
            empty: [plain, ~]
            single: hidden
            """.trimIndent(),
        )
        assertEquals(listOf("plain", "from-the-environment"), config.getSecretList("tokens").map { it.reveal() })
        assertEquals(emptyList(), config.getSecretList("missing"))
        assertTrue(
            assertFailsWith<ConfigurationValueException> { config.getSecretList("broken") }.message.orEmpty()
                .startsWith("Element 1 of list at \"broken\" uses \${env:VERSATIA_TEST_UNSET}, which is not set"),
        )
        assertEquals(
            "Element 1 of list at \"empty\" is empty, expected a secret: text, \${env:NAME}, \${file:PATH} or \${pluginfile:PATH}",
            assertFailsWith<ConfigurationValueException> { config.getSecretList("empty") }.message,
        )
        assertEquals("Value at \"single\" is a single value, expected a list of secrets", assertFailsWith<ConfigurationValueException> { config.getSecretList("single") }.message)
    }
}
