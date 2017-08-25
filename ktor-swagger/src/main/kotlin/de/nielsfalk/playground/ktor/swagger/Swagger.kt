@file:Suppress("MemberVisibilityCanPrivate", "unused")

package de.nielsfalk.playground.ktor.swagger

import de.nielsfalk.playground.ktor.swagger.ParameterInputType.body
import de.nielsfalk.playground.ktor.swagger.ParameterInputType.query
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.locations.location
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * @author Niels Falk
 */
typealias ModelName = String
typealias PropertyName = String
typealias Path = String
typealias Definition = Pair<ModelName, ModelData>
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
            val (modelName, modelData) = entityType.toDefinition()
            swagger.definitions.put(modelName, modelData)
            add(ModelParameter(entityType))
        }
        addAll(locationType.memberProperties.map { it.toParameter(location.path) })
    }
    val responses: Map<HttpStatus, Response> = metadata.responses.map {
        val (status, clazz) = it
        if (clazz != Unit::class) {
            swagger.definitions.put(clazz.modelName(), ModelData(clazz))
        }
        status.value.toString() to Response(status, clazz)
    }.toMap()
}

private fun <T, R> KProperty1<T, R>.toParameter(path: String): Parameter {
    val inputType = if (path.contains("{$name}")) ParameterInputType.path else query
    val description = name
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


class Response(httpStatusCode: HttpStatusCode, clazz: KClass<*>) {
    val description = if (clazz == Unit::class) httpStatusCode.description else clazz.responseDescription()
    val schema = if (clazz == Unit::class) null else ModelReference(clazz.modelName())
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
    val properties: Map<PropertyName, ModelProperty> =
            kClass.memberProperties
                    .map { it.name to it.toModelProperty() }
                    .toMap()
}

fun <T, R> KProperty1<T, R>.toModelProperty(): ModelProperty =
        when (returnType.toString()) {
            "kotlin.Int?" -> IntModelProperty()
            "kotlin.Int" -> IntModelProperty()
            "kotlin.String" -> StringModelProperty()

            else -> if (returnType.classifier.toString() == "class kotlin.collections.List") {
                val clazz: KClass<*> = returnType.arguments.first().type?.classifier as KClass<*>
                swagger.definitions.put(clazz.modelName(), ModelData(clazz))
                ArrayModelModelProperty(clazz.modelName())
            } else {
                TODO("please implement")
            }
        }

abstract class ModelProperty(val type: String)

class IntModelProperty : ModelProperty("integer") {
    val format = "int32"
}

class StringModelProperty : ModelProperty("string")

class ArrayModelModelProperty(modelName: ModelName) : ModelProperty("array") {
    val items = ModelReference(modelName)
}

inline fun <reified LOCATION : Any, reified ENTITY_TYPE : Any> Metadata.apply(method: HttpMethod) {
    val clazz = LOCATION::class.java
    val location = clazz.getAnnotation(location::class.java)
    applyResponseDefinitions()
    applyOperations(location, method, LOCATION::class, ENTITY_TYPE::class)
}

fun Metadata.applyResponseDefinitions() {
    swagger.definitions.putAll(responses.values.filter { it != Unit::class }.map { it.toDefinition() })
}

private fun KClass<*>.toDefinition(): Definition = modelName() to ModelData(this)

private fun KClass<*>.modelName(): ModelName = simpleName ?: toString()
