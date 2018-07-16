@file:Suppress("MemberVisibilityCanPrivate", "unused")

package de.nielsfalk.ktor.swagger

import de.nielsfalk.ktor.swagger.version.shared.Group
import de.nielsfalk.ktor.swagger.version.shared.ModelName
import de.nielsfalk.ktor.swagger.version.shared.OperationCreator
import de.nielsfalk.ktor.swagger.version.shared.Parameter
import de.nielsfalk.ktor.swagger.version.shared.ParameterInputType
import de.nielsfalk.ktor.swagger.version.shared.ParameterInputType.body
import de.nielsfalk.ktor.swagger.version.shared.ParameterInputType.query
import de.nielsfalk.ktor.swagger.version.shared.Property
import de.nielsfalk.ktor.swagger.version.shared.PropertyName
import de.nielsfalk.ktor.swagger.version.shared.ResponseCreator
import de.nielsfalk.ktor.swagger.version.shared.Tag
import de.nielsfalk.ktor.swagger.version.v3.Schema
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.feature
import io.ktor.client.call.TypeInfo
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties

/**
 * Gets the [Application.swagger] feature
 */
val ApplicationCall.swagger get() = application.swagger

/**
 * Gets the [Application.swagger] feature
 */
val Application.swagger get() = feature(SwaggerSupport)

fun Group.toList(): List<Tag> {
    return listOf(Tag(name))
}

class SpecVariation(
    internal val modelRoot: String,
    internal val reponseCreator: ResponseCreator,
    internal val operationCreator: OperationCreator
) {
    operator fun <R> invoke(use: SpecVariation.() -> R): R =
        this.use()

    fun <T, R> KProperty1<T, R>.toParameter(
        path: String,
        inputType: ParameterInputType = if (path.contains("{$name}")) ParameterInputType.path else query
    ): Pair<Parameter, Collection<TypeInfo>> {
        val schemaAnnotation = annotations.firstOrNull { it is Schema } as? Schema
        return if (schemaAnnotation != null) {
            val property = Property(
                `$ref` = "$modelRoot${schemaAnnotation.schema}"
            )
            Parameter.create(
                property,
                name,
                inputType,
                description = null,
                required = !returnType.isMarkedNullable
            ) to emptyTypeInfoList
        } else {
            toModelProperty().let {
                Parameter.create(
                    it.first,
                    name,
                    inputType,
                    required = !returnType.isMarkedNullable
                ) to it.second
            }
        }
    }

    internal fun BodyType.bodyParameter() =
        when (this) {
            is BodyFromReflection ->
                Parameter.create(
                    typeInfo.referenceProperty(),
                    name = "noReflectionBody",
                    description = typeInfo.modelName(),
                    `in` = body
                )
            is BodyFromSchema ->
                Parameter.create(
                    referenceProperty(),
                    name = "noReflectionBody",
                    description = name,
                    `in` = body
                )
        }

    fun <T, R> KProperty1<T, R>.toModelProperty(reifiedType: Type? = null): Pair<Property, Collection<TypeInfo>> =
        returnType.toModelProperty(reifiedType)

    fun KType.toModelProperty(reifiedType: Type?): Pair<Property, Collection<TypeInfo>> =
        resolveTypeInfo(reifiedType).let { typeInfo ->
            val type = typeInfo?.type ?: classifier as KClass<*>
            type.toModelProperty(this, typeInfo?.reifiedType as? ParameterizedType)
        }

    /**
     * @param returnType The return type of this [KClass] (used for generics like `List<String>` or List<T>`).
     * @param reifiedType The reified generic type captured. Used for looking up types by their generic name like `T`.
     */
    private fun KClass<*>.toModelProperty(
        returnType: KType? = null,
        reifiedType: ParameterizedType? = null
    ): Pair<Property, Collection<TypeInfo>> {
        return propertyTypes[qualifiedName?.removeSuffix("?")]
            ?: if (returnType != null && isCollectionType) {
                val returnTypeClassifier = returnType.classifier
                val returnArgumentType: KType? = when (returnTypeClassifier) {
                    is KClass<*> -> returnType.arguments.first().type
                    is KTypeParameter -> {
                        reifiedType!!.actualTypeArguments.first().toKType()
                    }
                    else -> unsuportedType(returnTypeClassifier)
                }
                val classifier = returnArgumentType?.classifier
                if (classifier.isCollectionType) {
                    /*
                     * Handle the case of nested collection types.
                     * For example: List<List<String>>
                     */
                    val kClass = classifier as KClass<*>
                    val items = kClass.toModelProperty(
                        returnType = returnArgumentType,
                        reifiedType = returnArgumentType.parameterize(reifiedType)
                    )
                    Property(items = items.first, type = "array") to items.second
                } else {
                    /*
                     * Handle the case of a collection that holds the type directly.
                     * For example: List<String> or List<T>
                     */
                    val items = when (classifier) {
                        is KClass<*> -> {
                            /*
                             * The type is explicit.
                             * For example: List<String>.
                             * classifier would be String::class.
                             */
                            classifier.toModelProperty()
                        }
                        is KTypeParameter -> {
                            /*
                             * The case that we need to figure out what the reified generic type is.
                             * For example: List<T>
                             * Need to figure out what the next level generic type would be.
                             */
                            val nextParameterizedType = reifiedType?.actualTypeArguments?.first()
                            when (nextParameterizedType) {
                                is Class<*> -> {
                                    /*
                                     * The type the collection is holding type is encoded in the reified type information.
                                     */
                                    nextParameterizedType.kotlin.toModelProperty()
                                }
                                is ParameterizedType -> {
                                    /*
                                     * The type the collection is holding is generic.
                                     */
                                    val kClass = (nextParameterizedType.rawType as Class<*>).kotlin
                                    kClass.toModelProperty(reifiedType = nextParameterizedType)
                                }
                                else -> unsuportedType(nextParameterizedType)
                            }
                        }
                        else -> unsuportedType(classifier)
                    }
                    Property(items = items.first, type = "array") to items.second
                }
            } else if (java.isEnum) {
                val enumConstants = (this).java.enumConstants
                Property(
                    enum = enumConstants.map { (it as Enum<*>).name },
                    type = "string"
                ) to emptyTypeInfoList
            } else {
                val typeInfo = when (reifiedType) {
                    is ParameterizedType -> TypeInfo(this, reifiedType)
                    else -> TypeInfo(this, this.java)
                }
                typeInfo.referenceProperty() to listOf(typeInfo)
            }
    }

    fun createModelData(typeInfo: TypeInfo): ModelDataWithDiscoveredTypeInfo {
        val collectedClassesToRegister = mutableListOf<TypeInfo>()
        val modelProperties =
            typeInfo.type.memberProperties.map {
                val propertiesWithCollected = it.toModelProperty(typeInfo.reifiedType)
                collectedClassesToRegister.addAll(propertiesWithCollected.second)
                it.name to propertiesWithCollected.first
            }.toMap()
        return ModelData(modelProperties) to collectedClassesToRegister
    }

    private fun BodyFromSchema.referenceProperty(): Property =
        Property(
            `$ref` = modelRoot + name,
            description = name,
            type = null
        )

    private fun TypeInfo.referenceProperty(): Property =
        Property(
            `$ref` = modelRoot + modelName(),
            description = modelName(),
            type = null
        )
}

fun TypeInfo.responseDescription(): String = modelName()

/**
 * Holds the [ModelData] that was created from a given [TypeInfo] along with any
 * additional [TypeInfo] that were encountered and must be converted to [ModelData].
 */
typealias ModelDataWithDiscoveredTypeInfo = Pair<ModelData, Collection<TypeInfo>>

class ModelData(val properties: Map<PropertyName, Property>)

private val propertyTypes = mapOf(
    Int::class to Property("integer", "int32"),
    Long::class to Property("integer", "int64"),
    String::class to Property("string"),
    Double::class to Property("number", "double"),
    Instant::class to Property("string", "date-time"),
    Date::class to Property("string", "date-time"),
    LocalDateTime::class to Property("string", "date-time"),
    LocalDate::class to Property("string", "date")
).mapKeys { it.key.qualifiedName }.mapValues { it.value to emptyList<TypeInfo>() }

internal fun <T, R> KProperty1<T, R>.returnTypeInfo(reifiedType: Type?): TypeInfo =
    returnType.resolveTypeInfo(reifiedType)!!

internal fun KType.resolveTypeInfo(reifiedType: Type?): TypeInfo? {
    val classifierLocal = classifier
    return when (classifierLocal) {
        is KTypeParameter -> {
            val typeNameToLookup = classifierLocal.name
            val reifiedClass = (reifiedType as ParameterizedType).typeForName(typeNameToLookup)
            val kotlinType = reifiedClass.rawKotlinKClass()
            return TypeInfo(kotlinType, reifiedClass)
        }
        is KClass<*> -> {
            this.parameterize(reifiedType)?.let { TypeInfo(classifierLocal, it) }
        }
        else -> unsuportedType(classifierLocal)
    }
}

internal fun Type.rawKotlinKClass() = when (this) {
    is Class<*> -> this.kotlin
    is ParameterizedType -> (this.rawType as Class<*>).kotlin
    else -> unsuportedType(this)
}

private fun KType.parameterize(reifiedType: Type?): ParameterizedType? =
    (reifiedType as? ParameterizedType)?.let {
        parameterize((classifier as KClass<*>).java, *it.actualTypeArguments)
    }

private val KClass<*>?.isCollectionType
    get() = this?.isSubclassOf(Collection::class) ?: false

private val KClassifier?.isCollectionType
    get() = (this as? KClass<*>).isCollectionType

private val emptyTypeInfoList = emptyList<TypeInfo>()

@PublishedApi
internal fun TypeInfo.modelName(): ModelName {
    fun KClass<*>.modelName(): ModelName = simpleName ?: toString()

    return if (type.java == reifiedType) {
        type.modelName()
    } else {
        fun ParameterizedType.modelName(): String =
            actualTypeArguments
                .map {
                    when (it) {
                        is Class<*> -> {
                            /*
                             * The type isn't parameterized.
                             */
                            it.kotlin.modelName()
                        }
                        is ParameterizedType -> {
                            /*
                             * The type is parameterized, create a TypeInfo for it and recurse to get the
                             * model name again.
                             */
                            TypeInfo((it.rawType as Class<*>).kotlin, it).modelName()
                        }
                        is WildcardType -> {
                            (it.upperBounds.first() as Class<*>).kotlin.modelName()
                        }
                        else -> unsuportedType(it)
                    }
                }
                .joinToString(separator = "And") { it }

        val genericsName = (reifiedType as ParameterizedType)
            .modelName()
        "${type.modelName()}Of$genericsName"
    }
}

private inline fun unsuportedType(type: Any?): Nothing {
    throw IllegalStateException("Unknown type ${type?.let { it::class }} $type")
}
