package dev.versatia.ksp.text

import dev.versatia.ksp.di.BeanDeclaration
import dev.versatia.ksp.di.BeanProblem
import dev.versatia.ksp.di.combinationsOf
import dev.versatia.ksp.di.quoted
import dev.versatia.ksp.di.requiredPlugins
import dev.versatia.text.Presenter
import dev.versatia.text.TextProcessors
import com.squareup.kotlinpoet.ClassName

/**
 * Compile-time checks of the beans `@ServerPresentation` makes: each implements `Presenter` for a
 * class, which the descriptor records among the bean's generic supertypes and which is neither
 * `Any` nor generic; and two of them present one type only if exactly one is `@Primary` wherever
 * `@ConditionalOnPlugin` lets both exist, the rule the runtime applies between plugins. See
 * docs/enhancements.md.
 */
internal object ServerPresentationCheck {
    private val presenter = Presenter::class.java.name
    private val any = ClassName("kotlin", "Any")

    fun validate(beans: List<BeanDeclaration>): List<BeanProblem> {
        val problems = mutableListOf<BeanProblem>()
        val byType = LinkedHashMap<ClassName, MutableList<BeanDeclaration>>()
        for (bean in beans.filter { bean -> bean.enhancements.any { it.processorClass == TextProcessors.SERVER_PRESENTATION } }) {
            val problem = problemOf(bean)
            if (problem != null) problems += BeanProblem(bean.node, problem) else byType.getOrPut(presentedType(bean)!!) { mutableListOf() } += bean
        }
        for ((type, presenting) in byType) if (presenting.size > 1) ambiguity(type, presenting)?.let { problems += BeanProblem(presenting.last().node, it) }
        return problems
    }

    private fun presentedType(bean: BeanDeclaration) = bean.genericSupertypes.firstOrNull { it.type.canonicalName == presenter }?.arguments?.singleOrNull()?.takeIf { it.arguments.isEmpty() }?.type

    private fun problemOf(bean: BeanDeclaration): String? {
        val name = bean.type.simpleNames.joinToString(".")
        val type = presentedType(bean)
        return when {
            bean.assignableTypes.none { it.canonicalName == presenter } ->
                "$name carries @ServerPresentation but is no Presenter; implement Presenter for the type it presents, as in class $name : Presenter<Player>"
            type == null -> "$name must present a class without type arguments, such as Presenter<Player>: a generic type would match its values whatever their type arguments"
            type == any -> "$name presents Any, which would change how every value of every plugin appears; present the type you mean"
            else -> null
        }
    }

    /** Why the beans presenting [type] can be ambiguous, or null when one of them is chosen wherever several exist. */
    private fun ambiguity(type: ClassName, presenting: List<BeanDeclaration>): String? {
        val conditions = presenting.associateWith { it.annotations.requiredPlugins() }
        for (enabled in combinationsOf(conditions.values.flatten().distinct())) {
            val present = presenting.filter { enabled.containsAll(conditions.getValue(it)) }
            val primaries = present.filter { it.primary }
            if (present.size < 2 || primaries.size == 1) continue
            val condition = if (enabled.isEmpty()) "" else " while ${enabled.quoted()} ${if (enabled.size == 1) "is" else "are"} enabled"
            val prefix = "${type.canonicalName} has several server presentations$condition: ${present.joinToString { it.type.canonicalName }}"
            return if (primaries.isEmpty()) "$prefix; mark the one to use @Primary, or remove the others"
            else "$prefix, and several are @Primary: ${primaries.joinToString { it.type.canonicalName }}; keep @Primary on one"
        }
        return null
    }
}
