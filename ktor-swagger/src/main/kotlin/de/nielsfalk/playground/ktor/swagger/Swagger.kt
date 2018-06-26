@file:Suppress("MemberVisibilityCanPrivate", "unused")

package de.nielsfalk.playground.ktor.swagger

import de.nielsfalk.playground.ktor.swagger.ParameterInputType.body
import de.nielsfalk.playground.ktor.swagger.ParameterInputType.query
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.feature
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties

typealias ModelName = String
typealias PropertyName = String
typealias Path = String
typealias Definitions = MutableMap<ModelName, ModelData>
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
    val definitions: Definitions = ConcurrentSkipListMap()
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
    metadata: Metadata,
    val responses: Map<HttpStatus, Response>,
    val parameters: List<Parameter>,
    location: Location,
    group: Group?,
    method: HttpMethod,
    locationType: KClass<*>,
    entityType: KClass<*>
) {
    val tags = group?.toList()
    val summary = metadata.summary ?: "${method.value} ${location.path}"
}

private fun Group.toList(): List<Tag> {
    return listOf(Tag(name))
}

fun <T, R> KProperty1<T, R>.toParameter(path: String, inputType: ParameterInputType = if (path.contains("{$name}")) ParameterInputType.path else query): Pair<Parameter, Collection<KClass<*>>> {
    return toModelProperty().let { Parameter(it.first, name, inputType, required = !returnType.isMarkedNullable) to it.second }
}

internal fun KClass<*>.bodyParameter() =
        Parameter(referenceProperty(),
                name = "body",
                description = modelName(),
                `in` = body
        )

class Response(httpStatusCode: HttpStatusCode, kClass: KClass<*>) {
    val description = if (kClass == Unit::class) httpStatusCode.description else kClass.responseDescription()
    val schema = if (kClass == Unit::class) null else ModelReference("#/definitions/" + kClass.modelName())
}

fun KClass<*>.responseDescription(): String = modelName()

class ModelReference(val `$ref`: String)

class Parameter(
    property: de.nielsfalk.playground.ktor.swagger.Property,
    val name: String,
    val `in`: ParameterInputType,
    val description: String = property.description ?: name,
    val required: Boolean = true,
    val type: String? = property.type,
    val format: String? = property.format,
    val enum: List<String>? = property.enum,
    val items: Property? = property.items,
    val schema: ModelReference? = property.`$ref`?.let { ModelReference(it) }
)

enum class ParameterInputType {
    query, path, body, header
}

fun createModelData(kClass: KClass<*>): Pair<ModelData, Collection<KClass<*>>> {
    val collectedClassesToRegister = mutableListOf<KClass<*>>()
    val modelProperties =
        kClass.memberProperties.map {
            val propertiesWithCollected = it.toModelProperty()
            collectedClassesToRegister.addAll(propertiesWithCollected.second)
            it.name to propertiesWithCollected.first
        }.toMap()
    return ModelData(modelProperties) to collectedClassesToRegister
}

class ModelData(val properties: Map<PropertyName, Property>)

val propertyTypes = mapOf(
        Int::class to Property("integer", "int32"),
        Long::class to Property("integer", "int64"),
        String::class to Property("string"),
        Double::class to Property("number", "double"),
        Instant::class to Property("string", "date-time"),
        Date::class to Property("string", "date-time"),
        LocalDateTime::class to Property("string", "date-time"),
        LocalDate::class to Property("string", "date")
).mapKeys { it.key.qualifiedName }.mapValues { it.value to emptyList<KClass<*>>() }

fun <T, R> KProperty1<T, R>.toModelProperty(): Pair<Property, Collection<KClass<*>>> =
        (returnType.classifier as KClass<*>)
                .toModelProperty(returnType)

private fun KClass<*>.toModelProperty(returnType: KType? = null): Pair<Property, Collection<KClass<*>>> =
        propertyTypes[qualifiedName?.removeSuffix("?")]
                ?: if (returnType != null && toString() == "class kotlin.collections.List") {
                    val kClass: KClass<*> = returnType.arguments.first().type?.classifier as KClass<*>
                    val items = kClass.toModelProperty()
                    Property(items = items.first, type = "array") to items.second
                } else if (java.isEnum) {
                    val enumConstants = (this).java.enumConstants
                    Property(enum = enumConstants.map { (it as Enum<*>).name }, type = "string") to emptyKClassList
                } else {
                    referenceProperty() to listOf(this)
                }

private fun KClass<*>.referenceProperty(): Property =
        Property(`$ref` = "#/definitions/" + modelName(),
                description = modelName(),
                type = null)

open class Property(
    val type: String?,
    val format: String? = null,
    val enum: List<String>? = null,
    val items: Property? = null,
    val description: String? = null,
    val `$ref`: String? = null
)

private val emptyKClassList = emptyList<KClass<*>>()

internal fun KClass<*>.modelName(): ModelName = simpleName ?: toString()

annotation class Group(val name: String)
