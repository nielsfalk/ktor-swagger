package de.nielsfalk.ktor.swagger.version.shared

import de.nielsfalk.ktor.swagger.Metadata
import de.nielsfalk.ktor.swagger.toList
import de.nielsfalk.ktor.swagger.version.v3.Example
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
typealias Methods = MutableMap<MethodName, OperationBase>

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

interface OperationBase {
    val responses: Map<HttpStatus, ResponseBase>
    val parameters: List<ParameterBase>
    val tags: List<Tag>?
    val summary: String
    val description: String?
}

interface OperationCreator {
    fun create(
        metadata: Metadata,
        responses: Map<HttpStatus, ResponseBase>,
        parameters: List<ParameterBase>,
        location: Location,
        group: Group?,
        method: HttpMethod,
        examples: Map<String, Example>
    ): OperationBase {
        val tags = group?.toList()
        val summary = metadata.summary ?: "${method.value} ${location.path}"
        return create(
            responses,
            parameters,
            tags,
            summary,
            metadata.description,
            examples
        )
    }

    fun create(
        responses: Map<HttpStatus, ResponseBase>,
        parameters: List<ParameterBase>,
        tags: List<Tag>?,
        summary: String,
        description: String?,
        examples: Map<String, Example>
    ): OperationBase
}

class ModelReference(val `$ref`: String) {
    companion object {
        fun create(modelName: String) = ModelReference(modelName)
    }
}

interface ParameterBase {
    val name: String
    val `in`: ParameterInputType
    val description: String?
    val required: Boolean
}

interface ParameterCreator {
    fun create(
        property: Property,
        name: String,
        `in`: ParameterInputType,
        description: String? = property.description ?: name,
        required: Boolean = true,
        examples: Map<String, Example> = emptyMap()
    ): ParameterBase
}

interface ResponseBase {
    val description: String
}

interface ResponseCreator {
    fun create(
        httpStatusCode: HttpStatusCode,
        typeInfo: TypeInfo,
        examples: Map<String, Example>
    ): ResponseBase

    fun create(
        modelName: String,
        examples: Map<String, Example>
    ): ResponseBase
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
    override val `$ref`: String? = null
) : RefHolder

interface RefHolder {
    val `$ref`: String?
}

annotation class Group(val name: String)
