package org.jetbrains.dukat.js.type.constraint.resolution

import org.jetbrains.dukat.astCommon.IdentifierEntity
import org.jetbrains.dukat.js.type.constraint.Constraint
import org.jetbrains.dukat.js.type.constraint.composite.CompositeConstraint
import org.jetbrains.dukat.js.type.constraint.immutable.resolved.BigIntTypeConstraint
import org.jetbrains.dukat.js.type.constraint.immutable.resolved.BooleanTypeConstraint
import org.jetbrains.dukat.js.type.constraint.immutable.resolved.NumberTypeConstraint
import org.jetbrains.dukat.js.type.constraint.immutable.resolved.StringTypeConstraint
import org.jetbrains.dukat.js.type.constraint.immutable.resolved.VoidTypeConstraint
import org.jetbrains.dukat.js.type.constraint.properties.ClassConstraint
import org.jetbrains.dukat.js.type.constraint.properties.FunctionConstraint
import org.jetbrains.dukat.js.type.type.anyNullableType
import org.jetbrains.dukat.js.type.type.booleanType
import org.jetbrains.dukat.js.type.type.numberType
import org.jetbrains.dukat.js.type.type.stringType
import org.jetbrains.dukat.js.type.type.voidType
import org.jetbrains.dukat.panic.raiseConcern
import org.jetbrains.dukat.tsmodel.ClassDeclaration
import org.jetbrains.dukat.tsmodel.FunctionDeclaration
import org.jetbrains.dukat.tsmodel.MemberDeclaration
import org.jetbrains.dukat.tsmodel.ModifierDeclaration
import org.jetbrains.dukat.tsmodel.ParameterDeclaration
import org.jetbrains.dukat.tsmodel.TopLevelDeclaration
import org.jetbrains.dukat.tsmodel.VariableDeclaration
import org.jetbrains.dukat.tsmodel.types.ParameterValueDeclaration
import org.jetbrains.dukat.tsmodel.types.TypeDeclaration

val EXPORT_MODIFIERS = listOf(ModifierDeclaration.EXPORT_KEYWORD)
val STATIC_MODIFIERS = listOf(ModifierDeclaration.STATIC_KEYWORD)

fun getVariableDeclaration(name: String, type: ParameterValueDeclaration) = VariableDeclaration(
    name = name,
    type = type,
    modifiers = EXPORT_MODIFIERS,
    initializer = null,
    uid = getUID()
)

fun Constraint.toParameterDeclaration(name: String) = ParameterDeclaration(
        name = name,
        type = this.toType() ?: anyNullableType,
        initializer = null,
        vararg = false,
        optional = false
)

fun FunctionConstraint.toDeclaration(name: String) = FunctionDeclaration(
        name = name,
        parameters = parameterConstraints.map { (name, constraint) -> constraint.toParameterDeclaration(name) },
        type = returnConstraints.toType() ?: voidType,
        typeParameters = emptyList(),
        modifiers = EXPORT_MODIFIERS,
        body = null,
        uid = getUID()
)

fun FunctionDeclaration.withStaticModifier(isStatic: Boolean) : FunctionDeclaration {
    return this.copy(modifiers = if (isStatic) STATIC_MODIFIERS else emptyList())
}

fun FunctionConstraint.toMemberDeclaration(name: String, isStatic: Boolean) : MemberDeclaration? {
    return this.toDeclaration(name).withStaticModifier(isStatic)
}

fun Constraint.toMemberDeclaration(name: String, isStatic: Boolean) : MemberDeclaration? {
    return when (this) {
        is FunctionConstraint -> this.toMemberDeclaration(name, isStatic)
        else -> raiseConcern("Cannot treat constraint of type <${this::class}> as member.") { null }
    }
}

fun ClassConstraint.toDeclaration(name: String) : ClassDeclaration {
    val members = mutableListOf<MemberDeclaration>()

    members.addAll(
            prototype.propertyNames.mapNotNull { memberName ->
                prototype[memberName]?.toMemberDeclaration(name = memberName, isStatic = false)
            }
    )

    members.addAll(
            propertyNames.mapNotNull { memberName ->
                this[memberName]?.toMemberDeclaration(name = memberName, isStatic = true)
            }
    )

    return ClassDeclaration(
            name = IdentifierEntity(name),
            members = members,
            typeParameters = emptyList(),
            parentEntities = emptyList(), //TODO support inheritance,
            modifiers = EXPORT_MODIFIERS,
            uid = getUID()
    )
}

fun Constraint.toType() : TypeDeclaration? {
    return when (this) {
        is NumberTypeConstraint -> numberType
        is BigIntTypeConstraint -> numberType
        is BooleanTypeConstraint -> booleanType
        is StringTypeConstraint -> stringType
        is VoidTypeConstraint -> voidType
        else -> anyNullableType
    }
}

fun Constraint.toDeclaration(name: String) : TopLevelDeclaration? {
    return when (this) {
        is ClassConstraint -> this.toDeclaration(name)
        is FunctionConstraint -> this.toDeclaration(name)
        is CompositeConstraint -> raiseConcern("Unexpected composited type for variable named '$name'. Should be resolved by this point!") { null }
        else -> this.toType()?.let { type -> getVariableDeclaration(name, type) }
    }
}