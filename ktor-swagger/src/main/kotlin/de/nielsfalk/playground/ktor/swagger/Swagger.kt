@file:Suppress("MemberVisibilityCanPrivate", "unused")

package de.nielsfalk.playground.ktor.swagger

import de.nielsfalk.playground.ktor.swagger.ParameterInputType.body
import de.nielsfalk.playground.ktor.swagger.ParameterInputType.query
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.locations.location
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties

/**
 * @author Niels Falk
 */
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

class Contact(
        val name: String? = null,
        val url: String? = null,
        val email: String? = null
)

class Operation(
        metadata: Metadata,
        location: location,
        method: HttpMethod,
        locationType: KClass<*>,
        entityType: KClass<*>) {
    val summary = metadata.summary ?: "${method.value} ${location.path}"
    val parameters = mutableListOf<Parameter>().apply {
        if (entityType != Unit::class) {
            addDefinition(entityType)
            add(ModelParameter(entityType))
        }
        addAll(locationType.memberProperties.map { it.toParameter(location.path) })
    }
    val responses: Map<HttpStatus, Response> = metadata.responses.map {
        val (status, kClass) = it
        if (kClass != Unit::class) {
            addDefinition(kClass)
        }
        status.value.toString() to Response(status, kClass)
    }.toMap()
}

private fun <T, R> KProperty1<T, R>.toParameter(path: String): Parameter {
    val inputType = if (path.contains("{$name}")) ParameterInputType.path else query
    val description = name
    toModelProperty()
    return when (returnType.toString()) {
        "kotlin.Int?" -> IntParameter(name, description, inputType)
        "kotlin.Int" -> IntParameter(name, description, inputType)
        "kotlin.String" -> StringParameter(name, description, inputType)
        else -> TODO()
    }
}

fun <LOCATION : Any, BODY_TYPE : Any> Metadata.applyOperations(location: location, method: HttpMethod, locationType: KClass<LOCATION>, entityType: KClass<BODY_TYPE>) {
    swagger.paths
            .getOrPut(location.path) { mutableMapOf() }
            .put(method.value.toLowerCase(),
                    Operation(this, location, method, locationType, entityType))
}


class Response(httpStatusCode: HttpStatusCode, kClass: KClass<*>) {
    val description = if (kClass == Unit::class) httpStatusCode.description else kClass.responseDescription()
    val schema = if (kClass == Unit::class) null else ModelReference(kClass.modelName())
}

fun KClass<*>.responseDescription(): String = modelName()

class ModelReference(modelName: ModelName) {
    val `$ref` = "#/definitions/" + modelName
}

abstract class Parameter(
        val type: String?,
        val name: String,
        val description: String = name,
        val `in`: ParameterInputType,
        val required: Boolean
)

class IntParameter(name: String,
                   description: String = name,
                   parameterInputType: ParameterInputType,
                   required: Boolean = true
) : Parameter("integer", name, description, parameterInputType, required) {
    val format = "int32"
}

class StringParameter(name: String,
                      description: String = name,
                      parameterInputType: ParameterInputType,
                      required: Boolean = true
) : Parameter("string", name, description, parameterInputType, required)

class ModelParameter(entityType: KClass<*>,
                     required: Boolean = true
) : Parameter(null, "body", entityType.modelName(), body, required) {
    val schema = ModelReference(entityType.modelName())
}

enum class ParameterInputType {
    query, path, body
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
                    Property(`$ref` = "#/definitions/" + modelName(),
                            description = modelName(),
                            type = null)
                }

open class Property(val type: String?,
                    val format: String? = null,
                    val enum: List<String>? = null,
                    val items: Property? = null,
                    val description: String? = null,
                    val `$ref`: String? = null)

inline fun <reified LOCATION : Any, reified ENTITY_TYPE : Any> Metadata.apply(method: HttpMethod) {
    val clazz = LOCATION::class.java
    val location = clazz.getAnnotation(location::class.java)
    applyResponseDefinitions()
    applyOperations(location, method, LOCATION::class, ENTITY_TYPE::class)
}

fun Metadata.applyResponseDefinitions() =
        responses.values.filter { it != Unit::class }
                .forEach { addDefinition(it) }


private fun addDefinition(kClass: KClass<*>) {
    if (kClass != Unit::class) {
        swagger.definitions.computeIfAbsent(kClass.modelName()) {
            ModelData(kClass)
        }
    }
}

private fun KClass<*>.modelName(): ModelName = simpleName ?: toString()
