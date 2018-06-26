@file:Suppress("MemberVisibilityCanPrivate", "unused")

package de.nielsfalk.playground.ktor.swagger

import de.nielsfalk.playground.ktor.swagger.ParameterInputType.body
import de.nielsfalk.playground.ktor.swagger.ParameterInputType.header
import de.nielsfalk.playground.ktor.swagger.ParameterInputType.query
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
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

val swagger = Swagger()

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
        metadata: Metadata,
        location: Location,
        group: group?,
        method: HttpMethod,
        locationType: KClass<*>,
        entityType: KClass<*>) {
    val tags = group?.toList()
    val summary = metadata.summary ?: "${method.value} ${location.path}"
    val parameters = mutableListOf<Parameter>().apply {
        if (entityType != Unit::class) {
            addDefinition(entityType)
            add(entityType.bodyParameter())
        }
        addAll(locationType.memberProperties.map { it.toParameter(location.path) })
        metadata.parameter?.let {
            addAll(it.memberProperties.map { it.toParameter(location.path, query) })
        }
        metadata.headers?.let {
            addAll(it.memberProperties.map { it.toParameter(location.path, header) })
        }
    }

    val responses: Map<HttpStatus, Response> = metadata.responses.map {
        val (status, kClass) = it
        addDefinition(kClass)
        status.value.toString() to Response(status, kClass)
    }.toMap()
}

private fun group.toList(): List<Tag> {
    return listOf(Tag(name))
}

fun <T, R> KProperty1<T, R>.toParameter(path: String, inputType: ParameterInputType = if (path.contains("{$name}")) ParameterInputType.path else query): Parameter {
    return Parameter(toModelProperty(), name, inputType, required = !returnType.isMarkedNullable)
}

private fun KClass<*>.bodyParameter() =
        Parameter(referenceProperty(),
                name = "body",
                description = modelName(),
                `in` = body
        )

fun <LOCATION : Any, BODY_TYPE : Any> Metadata.applyOperations(location: Location, group: group?, method: HttpMethod, locationType: KClass<LOCATION>, entityType: KClass<BODY_TYPE>) {
    swagger.paths
            .getOrPut(location.path) { mutableMapOf() }
            .put(method.value.toLowerCase(),
                    Operation(this, location, group, method, locationType, entityType))
}

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

class ModelData(kClass: KClass<*>) {
    val properties: Map<PropertyName, Property> =
            kClass.memberProperties
                    .map { it.name to it.toModelProperty() }
                    .toMap()
}

val propertyTypes = mapOf(
        Int::class to Property("integer", "int32"),
        Long::class to Property("integer", "int64"),
        String::class to Property("string"),
        Double::class to Property("number", "double"),
        Instant::class to Property("string", "date-time"),
        Date::class to Property("string", "date-time"),
        LocalDateTime::class to Property("string", "date-time"),
        LocalDate::class to Property("string", "date")
).mapKeys { it.key.qualifiedName }

fun <T, R> KProperty1<T, R>.toModelProperty(): Property =
        (returnType.classifier as KClass<*>)
                .toModelProperty(returnType)

private fun KClass<*>.toModelProperty(returnType: KType? = null): Property =
        propertyTypes[qualifiedName?.removeSuffix("?")] ?:
                if (returnType != null && toString() == "class kotlin.collections.List") {
                    val kClass: KClass<*> = returnType.arguments.first().type?.classifier as KClass<*>
                    Property(items = kClass.toModelProperty(), type = "array")
                } else if (java.isEnum) {
                    val enumConstants = (this).java.enumConstants
                    Property(enum = enumConstants.map { (it as Enum<*>).name }, type = "string")
                } else {
                    addDefinition(this)
                    referenceProperty()
                }

private fun KClass<*>.referenceProperty(): Property =
        Property(`$ref` = "#/definitions/" + modelName(),
                description = modelName(),
                type = null)

open class Property(val type: String?,
                    val format: String? = null,
                    val enum: List<String>? = null,
                    val items: Property? = null,
                    val description: String? = null,
                    val `$ref`: String? = null)

inline fun <reified LOCATION : Any, reified ENTITY_TYPE : Any> Metadata.apply(method: HttpMethod) {
    val clazz = LOCATION::class.java
    val location = clazz.getAnnotation(Location::class.java)
    val tags = clazz.getAnnotation(group::class.java)
    applyResponseDefinitions()
    applyOperations(location, tags, method, LOCATION::class, ENTITY_TYPE::class)
}

fun Metadata.applyResponseDefinitions() =
        responses.values.forEach { addDefinition(it) }


private fun addDefinition(kClass: KClass<*>) {
    if (kClass != Unit::class) {
        swagger.definitions.computeIfAbsent(kClass.modelName()) {
            ModelData(kClass)
        }
    }
}

private fun KClass<*>.modelName(): ModelName = simpleName ?: toString()

annotation class group(val name: String)
