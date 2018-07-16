package de.nielsfalk.ktor.swagger.version.shared

import de.nielsfalk.ktor.swagger.Metadata
import de.nielsfalk.ktor.swagger.toList
import io.ktor.client.call.TypeInfo
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location

typealias ModelName = String
typealias PropertyName = String
typealias Path = String
typealias Paths = MutableMap<Path, Methods>
typealias MethodName = String
typealias HttpStatus = String
typealias Methods = MutableMap<MethodName, Operation>

interface CommonBase {
    val info: Information?
    val paths: Paths
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
    val responses: Map<HttpStatus, ResponseBase>,
    val parameters: List<Parameter>,
    val tags: List<Tag>?,
    val summary: String
) {

    companion object {
        fun create(
            metadata: Metadata,
            responses: Map<HttpStatus, ResponseBase>,
            parameters: List<Parameter>,
            location: Location,
            group: Group?,
            method: HttpMethod
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

class ModelReference(val `$ref`: String) {
    companion object {
        fun create(modelName: String) = ModelReference(modelName)
    }
}

class Parameter(
    val name: String,
    val `in`: ParameterInputType,
    val description: String?,
    val required: Boolean,
    /**
     * Not supported in OpenAPI v3.
     */
    val type: String? = null,
    /**
     * Not supported in OpenAPI v3.
     */
    val format: String? = null,
    /**
     * Not supported in OpenAPI v3.
     */
    val enum: List<String>? = null,
    /**
     * Not supported in OpenAPI v3.
     */
    val items: Property? = null,
    val schema: Any? = null
) {
    companion object {
        fun create(
            property: Property,
            name: String,
            `in`: ParameterInputType,
            description: String? = property.description ?: name,
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

interface ResponseBase {
    val description: String
}

interface ResponseCreator {
    fun create(httpStatusCode: HttpStatusCode, typeInfo: TypeInfo): ResponseBase

    fun create(modelName: String): ResponseBase
}

enum class ParameterInputType {
    query,
    path,
    /**
     * Not supported in OpenAPI v3.
     */
    body,
    header
}

data class Property(
    val type: String? = null,
    val format: String? = null,
    val enum: List<String>? = null,
    val items: Property? = null,
    val description: String? = null,
    val `$ref`: String? = null
)

annotation class Group(val name: String)
