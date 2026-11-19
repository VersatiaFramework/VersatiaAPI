package dev.versatia.ksp

import dev.versatia.Phase
import dev.versatia.di.BeanProcessors
import dev.versatia.engine.descriptor.AnnotationOccurrence
import dev.versatia.engine.descriptor.TypeReference
import dev.versatia.text.ServerPresentation
import dev.versatia.text.TextProcessors
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/** `@ServerPresentation` beans and the generic supertypes every described class records. */
@OptIn(ExperimentalCompilerApi::class)
class ServerPresentationTest : ProcessorTest() {
    private fun presenters(code: String) = SourceFile.kotlin(
        "Presenters.kt",
        """
        package com.example.alpha

        import dev.versatia.ConditionalOnPlugin
        import dev.versatia.di.Primary
        import dev.versatia.text.Presenter
        import dev.versatia.text.ServerPresentation
        import dev.versatia.text.presentation
        import net.kyori.adventure.text.Component.text
        import org.bukkit.entity.Player

        $code
        """,
    )

    @Test
    fun `a server presentation is a local bean recording the type it presents, through a generic superclass too`() {
        val result = compile(
            presenters(
                """
                @dev.versatia.di.LocalBean
                class Stats

                @ServerPresentation(style = "hero")
                class PlayerCard(val stats: Stats) : Presenter<Player> {
                    override fun present(value: Player) = presentation { component { text(value.name) } }
                }

                abstract class Card<T : Any> : Presenter<T>, Comparable<List<T>> {
                    override fun compareTo(other: List<T>) = 0
                }

                @ServerPresentation
                class StatsCard : Card<Stats>() {
                    override fun present(value: Stats) = presentation { property("kills") { 0 } }
                }

                @dev.versatia.di.LocalBean
                class Holder : Comparable<List<*>> {
                    override fun compareTo(other: List<*>) = 0
                }
                """,
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val descriptor = result.descriptor()
        val card = descriptor.elements.filter { it.target.className == "com.example.alpha.PlayerCard" }
        assertEquals(listOf(TextProcessors.SERVER_PRESENTATION, BeanProcessors.LOCAL), card.map { it.processorClass })
        assertEquals(setOf(Phase.PLUGIN_START_ONLY), card.map { it.phase }.toSet())
        assertEquals(AnnotationOccurrence(ServerPresentation::class.java.name, mapOf("style" to "hero")), card.first().annotation)
        val targets = descriptor.classTargets()
        assertEquals(listOf(TypeReference("dev.versatia.text.Presenter", listOf(TypeReference("org.bukkit.entity.Player")))), targets.getValue("com.example.alpha.PlayerCard").genericSupertypes)
        assertEquals(
            listOf(
                TypeReference("com.example.alpha.Card", listOf(TypeReference("com.example.alpha.Stats"))),
                TypeReference("dev.versatia.text.Presenter", listOf(TypeReference("com.example.alpha.Stats"))),
                TypeReference("kotlin.Comparable", listOf(TypeReference("kotlin.collections.List", listOf(TypeReference("com.example.alpha.Stats"))))),
            ),
            targets.getValue("com.example.alpha.StatsCard").genericSupertypes,
        )
        assertEquals(emptyList(), targets.getValue("com.example.alpha.Holder").genericSupertypes, "a star projection is not recorded")
        assertEquals(emptyList(), targets.getValue("com.example.alpha.Stats").genericSupertypes)
    }

    @Test
    fun `rejects a server presentation that presents no class of its own`() {
        val result = compile(
            presenters(
                """
                @ServerPresentation
                class NoPresenter

                @ServerPresentation
                class ListCard : Presenter<List<String>> {
                    override fun present(value: List<String>) = presentation { }
                }

                @ServerPresentation
                class EverythingCard : Presenter<Any> {
                    override fun present(value: Any) = presentation { }
                }

                @ServerPresentation
                object ObjectCard : Presenter<Player> {
                    override fun present(value: Player) = presentation { }
                }
                """,
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "NoPresenter carries @ServerPresentation but is no Presenter; implement Presenter for the type it presents")
        assertContains(result.messages, "ListCard must present a class without type arguments, such as Presenter<Player>")
        assertContains(result.messages, "EverythingCard presents Any, which would change how every value of every plugin appears")
        assertContains(result.messages, "ObjectCard cannot be a bean: it is an object")
    }

    @Test
    fun `two server presentations of one type need exactly one Primary wherever both exist`() {
        val ambiguous = compile(
            presenters(
                """
                @ServerPresentation
                class FirstCard : Presenter<Player> {
                    override fun present(value: Player) = presentation { }
                }

                @ServerPresentation @ConditionalOnPlugin("Vault")
                class VaultCard : Presenter<Player> {
                    override fun present(value: Player) = presentation { }
                }

                @ServerPresentation @Primary
                class PrimaryCard : Presenter<org.bukkit.OfflinePlayer> {
                    override fun present(value: org.bukkit.OfflinePlayer) = presentation { }
                }

                @ServerPresentation @Primary
                class OtherPrimaryCard : Presenter<org.bukkit.OfflinePlayer> {
                    override fun present(value: org.bukkit.OfflinePlayer) = presentation { }
                }
                """,
            ),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, ambiguous.exitCode)
        assertContains(
            ambiguous.messages,
            "org.bukkit.entity.Player has several server presentations while \"Vault\" is enabled: com.example.alpha.FirstCard, com.example.alpha.VaultCard; mark the one to use @Primary",
        )
        assertContains(ambiguous.messages, "org.bukkit.OfflinePlayer has several server presentations: com.example.alpha.PrimaryCard, com.example.alpha.OtherPrimaryCard, and several are @Primary")

        val chosen = compile(
            presenters(
                """
                @ServerPresentation
                class FirstCard : Presenter<Player> {
                    override fun present(value: Player) = presentation { }
                }

                @ServerPresentation @ConditionalOnPlugin("Vault") @Primary
                class VaultCard : Presenter<Player> {
                    override fun present(value: Player) = presentation { }
                }

                @ServerPresentation
                class OfflineCard : Presenter<org.bukkit.OfflinePlayer> {
                    override fun present(value: org.bukkit.OfflinePlayer) = presentation { }
                }
                """,
            ),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, chosen.exitCode, chosen.messages)
        assertFalse("several server presentations" in chosen.messages)
    }
}
