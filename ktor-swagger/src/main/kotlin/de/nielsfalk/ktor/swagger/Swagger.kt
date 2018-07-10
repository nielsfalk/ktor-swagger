@file:Suppress("MemberVisibilityCanPrivate", "unused")

package de.nielsfalk.ktor.swagger

import de.nielsfalk.ktor.swagger.ParameterInputType.body
import de.nielsfalk.ktor.swagger.ParameterInputType.query
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.feature
import io.ktor.client.call.TypeInfo
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
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

typealias ModelName = String
typealias PropertyName = String
typealias Path = String
typealias Definitions = MutableMap<ModelName, Any>
typealias Paths = MutableMap<Path, Methods>
typealias MethodName = String
typealias HttpStatus = String
typealias Methods = MutableMap<MethodName, Operation>

/**
 * Gets the [Application.swagger] feature
 */
val ApplicationCall.swagger get() = application.swagger

/**
 * Gets the [Application.swagger] feature
 */
val Application.swagger get() = feature(SwaggerSupport)

class Swagger {
    val swagger = "2.0"
    var info: Information? = null
    val paths: Paths = mutableMapOf()
    val definitions: Definitions = mutableMapOf()
}

class Information(
    val description: String? = null,
    val version: String? = null,
    val title: String? = null,
    val contact: Contact? = null
)

data class Tag(
    val name: String
)

class Contact(
    val name: String? = null,
    val url: String? = null,
    val email: String? = null
)

class Operation(
    val responses: Map<HttpStatus, Response>,
    val parameters: List<Parameter>,
    val tags: List<Tag>?,
    val summary: String
) {

    companion object {
        fun create(
            metadata: Metadata,
            responses: Map<HttpStatus, Response>,
            parameters: List<Parameter>,
            location: Location,
            group: Group?,
            method: HttpMethod,
            locationType: KClass<*>
        ): Operation {
            val tags = group?.toList()
            val summary = metadata.summary ?: "${method.value} ${location.path}"
            return Operation(
                responses,
                parameters,
                tags,
                summary
            )
        }
    }
}

private fun Group.toList(): List<Tag> {
    return listOf(Tag(name))
}

fun <T, R> KProperty1<T, R>.toParameter(
    path: String,
    inputType: ParameterInputType = if (path.contains("{$name}")) ParameterInputType.path else query
): Pair<Parameter, Collection<TypeInfo>> {
    return toModelProperty().let {
        Parameter.create(
            it.first,
            name,
            inputType,
            required = !returnType.isMarkedNullable
        ) to it.second
    }
}

internal fun ReceiveType.bodyParameter() =
    when (this) {
        is ReceiveFromReflection ->
            Parameter.create(
                typeInfo.referenceProperty(),
                name = "body",
                description = typeInfo.modelName(),
                `in` = body
            )
        is ReceiveSchema ->
            Parameter.create(
                referenceProperty(),
                name = "body",
                description = name,
                `in` = body
            )
    }

class Response(
    val description: String,
    val schema: ModelReference?
) {

    companion object {
        fun create(httpStatusCode: HttpStatusCode, typeInfo: TypeInfo): Response {
            return Response(
                description = if (typeInfo.type == Unit::class) httpStatusCode.description else typeInfo.responseDescription(),
                schema = if (typeInfo.type == Unit::class) null else ModelReference.create(
                    typeInfo.modelName()
                )
            )
        }

        fun create(modelName: String): Response {
            return Response(
                description = modelName,
                schema = ModelReference.create(modelName)
            )
        }
    }
}

fun TypeInfo.responseDescription(): String = modelName()

class ModelReference(val `$ref`: String) {
    companion object {
        fun create(modelName: String) = ModelReference(
            "#/definitions/$modelName"
        )
    }
}

class Parameter(
    val name: String,
    val `in`: ParameterInputType,
    val description: String,
    val required: Boolean,
    val type: String? = null,
    val format: String? = null,
    val enum: List<String>? = null,
    val items: Property? = null,
    val schema: ModelReference? = null
) {
    companion object {
        fun create(
            property: Property,
            name: String,
            `in`: ParameterInputType,
            description: String = property.description ?: name,
            required: Boolean = true
        ): Parameter {
            return Parameter(
                name = name,
                `in` = `in`,
                description = description,
                required = required,
                type = property.type,
                format = property.format,
                enum = property.enum,
                items = property.items,
                schema = property.`$ref`?.let { ModelReference(it) }
            )
        }
    }
}

enum class ParameterInputType {
    query, path, body, header
}

/**
 * Holds the [ModelData] that was created from a given [TypeInfo] along with any
 * additional [TypeInfo] that were encountered and must be converted to [ModelData].
 */
typealias ModelDataWithDiscoveredTypeInfo = Pair<ModelData, Collection<TypeInfo>>

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

fun <T, R> KProperty1<T, R>.toModelProperty(reifiedType: Type? = null): Pair<Property, Collection<TypeInfo>> =
    returnType.toModelProperty(reifiedType)

fun KType.toModelProperty(reifiedType: Type?): Pair<Property, Collection<TypeInfo>> =
    resolveTypeInfo(reifiedType).let { typeInfo ->
        val type = typeInfo?.type ?: classifier as KClass<*>
        type.toModelProperty(this, typeInfo?.reifiedType as? ParameterizedType)
    }

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

private fun ReceiveSchema.referenceProperty(): Property =
    Property(
        `$ref` = "#/definitions/" + name,
        description = name,
        type = null
    )

private fun TypeInfo.referenceProperty(): Property =
    Property(
        `$ref` = "#/definitions/" + modelName(),
        description = modelName(),
        type = null
    )

data class Property(
    val type: String?,
    val format: String? = null,
    val enum: List<String>? = null,
    val items: Property? = null,
    val description: String? = null,
    val `$ref`: String? = null
)

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

annotation class Group(val name: String)
