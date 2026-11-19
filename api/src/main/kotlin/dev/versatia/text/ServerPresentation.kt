package dev.versatia.text

import dev.versatia.Phase
import dev.versatia.VersatiaEnhancement
import dev.versatia.VersatiaInternalApi
import dev.versatia.di.LocalBean

/**
 * Presents a type in the messages of every plugin on the server: the annotated class implements
 * [Presenter] for that type, `class PlayerCardPresenter(...) : Presenter<Player>`, and is a local
 * bean of its plugin, so its constructor is injected and `@ConditionalOnPlugin` and `@Primary`
 * apply. It is in use from the moment the plugin's beans exist, before its start functions run,
 * until the plugin stops; a reload keeps it, like every bean.
 *
 * The presentations of the server come first: for the same type a server presentation wins over
 * what a plugin registers with [Text.present], both win over the built-in ones, and among them all
 * the type nearest to the value's class comes first. A presentation replaces only what it defines:
 * one that adds `{player.rank}` leaves `{player}` and `{player.ping}` to the next presentation of
 * the value. Two plugins presenting the same type fail the start of the second one, unless exactly
 * one of them is `@Primary`.
 *
 * [style] names the server's style the component is shown in, as `text.present(style = ...)` does.
 * The style is a part like the others: without one the value keeps the style of its type, so a
 * card of a player still looks like every player, and a style named without a component restyles
 * the component of the next presentation. A name that cannot be a style fails the plugin's start.
 * See https://versatia.dev/docs/messages/arguments#server-presentations.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@LocalBean
@VersatiaEnhancement(TextProcessors.SERVER_PRESENTATION, enhancementPhase = Phase.PLUGIN_START_ONLY)
annotation class ServerPresentation(val style: String = "")

/** The VersatiaCore processors of this package, by name: the API does not see the runtime. */
@VersatiaInternalApi
object TextProcessors {
    const val SERVER_PRESENTATION = "dev.versatia.text.ServerPresentationEnhancementProcessor"
}
