@file:Suppress("MemberVisibilityCanPrivate", "unused")

package de.nielsfalk.playground.ktor.swagger

import de.nielsfalk.playground.ktor.swagger.ParameterInputType.body
import de.nielsfalk.playground.ktor.swagger.ParameterInputType.query
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.feature
import io.ktor.client.call.TypeInfo
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import org.apache.commons.lang3.reflect.TypeUtils
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
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
                schema = if (typeInfo.type == Unit::class) null else ModelReference.create(typeInfo.modelName())
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
            property: de.nielsfalk.playground.ktor.swagger.Property,
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
typealias ModelDataWithDiscovereredTypeInfo = Pair<ModelData, Collection<TypeInfo>>

fun createModelData(typeInfo: TypeInfo): ModelDataWithDiscovereredTypeInfo {
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
    (returnType.classifier as KClass<*>)
        .toModelProperty(returnType, reifiedType)

internal fun <T, R> KProperty1<T, R>.returnTypeInfo(reifiedType: Type?): TypeInfo =
    TypeInfo(returnType.classifier as KClass<*>, returnType.parameterize(reifiedType)!!)

private fun KType.parameterize(reifiedType: Type?): ParameterizedType? =
    (reifiedType as? ParameterizedType)?.let {
        TypeUtils.parameterize((classifier as KClass<*>).java, *it.actualTypeArguments)
    }

private val collectionTypes = setOf("class kotlin.collections.List", "class kotlin.collections.Set")

/**
 * @param returnType The return type of this [KClass] (used for generics like `List<String>` or List<T>`.
 * @param reifiedType The reified generic type captured. Used for looking up types by their generic name like `T`.
 */
private fun KClass<*>.toModelProperty(returnType: KType? = null, reifiedType: Type? = null): Pair<Property, Collection<TypeInfo>> =
    propertyTypes[qualifiedName?.removeSuffix("?")]
        ?: if (returnType != null && collectionTypes.contains(toString())) {
            val returnArgumentType = returnType.arguments.first().type
            val classifier = returnArgumentType?.classifier
            if (collectionTypes.contains(classifier.toString())) {
                /*
                 * Handle the case of nested collection types.
                 */
                val kClass = classifier as KClass<*>
                val items = kClass.toModelProperty(returnType = returnArgumentType, reifiedType = returnArgumentType.parameterize(reifiedType))
                Property(items = items.first, type = "array") to items.second
            } else {
                /*
                 * Handle the case of a collection that holds the type directly.
                 */
                val kClass = when (classifier) {
                    is KClass<*> -> classifier
                    is KTypeParameter -> {
                        /*
                         * The case that we need to figure out what the reified generic type is.
                         */
                        ((reifiedType as ParameterizedType).actualTypeArguments.first() as Class<*>).kotlin
                    }
                    else -> throw IllegalStateException("Unknown type $classifier")
                }
                val items = kClass.toModelProperty(reifiedType = reifiedType)
                Property(items = items.first, type = "array") to items.second
            }
        } else if (java.isEnum) {
            val enumConstants = (this).java.enumConstants
            Property(enum = enumConstants.map { (it as Enum<*>).name }, type = "string") to emptyTypeInfoList
        } else {
            val typeInfo = when (reifiedType) {
                is ParameterizedType ->
                    if (returnType != null) {
                        TypeInfo(this, returnType.parameterize(reifiedType)!!)
                    } else {
                        TypeInfo(this, reifiedType.actualTypeArguments?.first()!!)
                    }
                else -> TypeInfo(this, this.java)
            }
            typeInfo.referenceProperty() to listOf(typeInfo)
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
        val genericsName =
            (reifiedType as ParameterizedType)
                .actualTypeArguments
                .map { it as Class<*> }
                .joinToString(separator = "And") { it.simpleName }
        "${type.modelName()}Of$genericsName"
    }
}

annotation class Group(val name: String)
