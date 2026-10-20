package com.github.marcoral.versatia.ksp

import com.github.marcoral.versatia.engine.descriptor.MemberOwner
import com.github.marcoral.versatia.ksp.model.TypeDeclaration
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName

/** Whether generated code in the plugin's own module may refer to this declaration. */
internal fun KSDeclaration.isReachable(): Boolean = getVisibility() in REACHABLE && parentDeclaration?.isReachable() != false

private val REACHABLE = setOf(Visibility.PUBLIC, Visibility.INTERNAL)

/** The class itself and every supertype except `Any`, type aliases expanded. */
internal fun KSClassDeclaration.assignableTypes() = (listOf(toClassName()) + getAllSuperTypes()
    .mapNotNull { (it.expanded().declaration as? KSClassDeclaration)?.toClassName() }
    .filter { it != ANY })
    .distinct()

private val ANY = ClassName("kotlin", "Any")

/**
 * The type with a type alias replaced by what it stands for, nullability kept; the alias's type
 * parameters are substituted where the expansion uses them as its own type arguments.
 */
internal fun KSType.expanded(): KSType {
    val alias = declaration as? KSTypeAlias ?: return this
    val target = alias.type.resolve()
    val bound = alias.typeParameters.map { it.name.asString() }.zip(arguments).toMap()
    val substituted = if (bound.isEmpty()) target else target.replace(
        target.arguments.map { argument -> (argument.type?.resolve()?.declaration as? KSTypeParameter)?.let { bound[it.name.asString()] } ?: argument },
    )
    val expanded = substituted.expanded()
    return if (isMarkedNullable) expanded.makeNullable() else expanded
}

/** The facts of this type the descriptor records, or null when it is not a class type with class type arguments. */
internal fun KSType.declared(): TypeDeclaration? {
    val clazz = declaration as? KSClassDeclaration ?: return null
    val arguments = this.arguments.map { it.type?.resolve()?.expanded()?.declared() ?: return null }
    return TypeDeclaration(clazz.toClassName(), arguments, isMarkedNullable)
}

/** Whether generated code may name this type with all its type arguments. */
internal fun KSType.isReachable(): Boolean = declaration.isReachable() && arguments.all { it.type?.resolve()?.expanded()?.isReachable() == true }

internal const val JAVA_PLUGIN = "org.bukkit.plugin.java.JavaPlugin"

/** Named, not referenced: the processor's classpath has no Paper API, so the class must never load. */
internal const val VERSATIA_PLUGIN = "com.github.marcoral.versatia.VersatiaPlugin"

internal fun KSClassDeclaration.extendsJavaPlugin() = getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == JAVA_PLUGIN }

/** Abstract or sealed, which KSP reports apart: either way the class has no instances of its own. */
internal fun KSClassDeclaration.isAbstract() = Modifier.ABSTRACT in modifiers || Modifier.SEALED in modifiers

internal fun KSClassDeclaration.isConcreteClass() = classKind == ClassKind.CLASS && !isAbstract()

/** Whether no other class can extend this one, so that only it can provide its type. */
internal fun KSClassDeclaration.isFinal() = when (classKind) {
    ClassKind.OBJECT, ClassKind.ENUM_CLASS, ClassKind.ENUM_ENTRY -> true
    ClassKind.CLASS -> Modifier.FINAL in modifiers && modifiers.none { it == Modifier.OPEN || it == Modifier.ABSTRACT || it == Modifier.SEALED }
    else -> false
}

/** The name with the enclosing classes, as written in Kotlin: `Shop.Companion`. */
internal fun KSDeclaration.displayName(): String = generateSequence(this) { it.parentDeclaration }.toList().asReversed().joinToString(".") { it.simpleName.asString() }

/**
 * Who may own enhanced members: the plugin's main class with its superclasses, the beans, and the
 * other classes a subclass enhancement creates, by qualified name.
 */
internal class Owners(val pluginLineage: Set<String>, val beanTypes: Set<String>, val subclassTypes: Set<String> = emptySet())

/**
 * Who holds an instance of this class for the runtime to call a function on or set a property of:
 * the plugin itself, an `object`, a bean or, for a [value] property only, the instance a subclass
 * enhancement creates; null for any other class, whose instance the runtime could not obtain;
 * [notAnOwner] says why.
 */
internal fun KSClassDeclaration.memberOwner(owners: Owners, value: Boolean = false) = when {
    classKind == ClassKind.OBJECT && !isCompanionObject -> MemberOwner.OBJECT
    qualifiedName?.asString() in owners.pluginLineage -> MemberOwner.PLUGIN
    qualifiedName?.asString() in owners.beanTypes -> MemberOwner.INSTANCE
    value && qualifiedName?.asString() in owners.subclassTypes -> MemberOwner.SUBCLASS_INSTANCE
    else -> null
}

/** Why the runtime cannot reach an instance of this class for a member, a [value] property or another; see [memberOwner]. */
internal fun KSClassDeclaration.notAnOwner(owners: Owners, value: Boolean = false): String {
    val name = displayName()
    return when {
        isCompanionObject -> "$name is a companion object, which the runtime does not reach; move it into a named object, the plugin's main class or a bean"
        extendsJavaPlugin() -> "$name extends JavaPlugin but is neither the plugin's main class nor a base class of it"
        qualifiedName?.asString() in owners.subclassTypes ->
            "$name is created by a subclass enhancement, which sets only its @Value properties; give it a bean scope such as @LocalBean to enhance its other members"
        value -> "$name is neither the plugin's main class, an object, a bean nor a class a subclass enhancement creates, such as a Listener or a BackgroundTask"
        else -> "$name is neither the plugin's main class, an object nor a bean"
    }
}

/** [declaration] and every class nested in it, at any depth. */
internal fun classesIn(declaration: KSDeclaration): Sequence<KSClassDeclaration> =
    if (declaration !is KSClassDeclaration) emptySequence() else sequenceOf(declaration) + declaration.declarations.flatMap(::classesIn)

/** The constant an annotation argument names when it is an enum entry, however KSP delivers it. */
internal fun enumEntryName(value: Any?): String? = when (value) {
    is Enum<*> -> value.name
    is KSClassDeclaration -> value.simpleName.asString().takeIf { value.classKind == ClassKind.ENUM_ENTRY }
    is KSType -> enumEntryName(value.declaration as? KSClassDeclaration)
    else -> null
}
