package de.nielsfalk.ktor.swagger.version.v2

import de.nielsfalk.ktor.swagger.CustomContentTypeResponse
import de.nielsfalk.ktor.swagger.HttpCodeResponse
import de.nielsfalk.ktor.swagger.JsonResponseFromReflection
import de.nielsfalk.ktor.swagger.JsonResponseSchema
import de.nielsfalk.ktor.swagger.modelName
import de.nielsfalk.ktor.swagger.responseDescription
import de.nielsfalk.ktor.swagger.version.shared.CommonBase
import de.nielsfalk.ktor.swagger.version.shared.HttpStatus
import de.nielsfalk.ktor.swagger.version.shared.Information
import de.nielsfalk.ktor.swagger.version.shared.ModelName
import de.nielsfalk.ktor.swagger.version.shared.ModelOrModelReference
import de.nielsfalk.ktor.swagger.version.shared.OperationBase
import de.nielsfalk.ktor.swagger.version.shared.OperationCreator
import de.nielsfalk.ktor.swagger.version.shared.ParameterBase
import de.nielsfalk.ktor.swagger.version.shared.ParameterCreator
import de.nielsfalk.ktor.swagger.version.shared.ParameterInputType
import de.nielsfalk.ktor.swagger.version.shared.Paths
import de.nielsfalk.ktor.swagger.version.shared.Property
import de.nielsfalk.ktor.swagger.version.shared.ResponseBase
import de.nielsfalk.ktor.swagger.version.shared.ResponseCreator
import de.nielsfalk.ktor.swagger.version.shared.Tag
import de.nielsfalk.ktor.swagger.version.v3.Example
import io.ktor.client.call.TypeInfo
import io.ktor.http.HttpStatusCode

typealias Definitions = MutableMap<ModelName, Any>

class Swagger : CommonBase {
    val swagger = "2.0"
    override var info: Information? = null
    override val paths: Paths = mutableMapOf()
    val definitions: Definitions = mutableMapOf()
}

class Response(
    override val description: String,
    val schema: ModelOrModelReference? = null,
    val produces: List<String>? = listOf("application/json")
) : ResponseBase {

    companion object : ResponseCreator {
        override fun create(response: HttpCodeResponse): ResponseBase? {
            return response.responseTypes.firstOrNull()?.let {
                when (it) {
                    is JsonResponseFromReflection -> create(
                        response.statusCode,
                        it.type,
                        response.description
                    )
                    is JsonResponseSchema -> create(
                        it.name
                    )
                    is CustomContentTypeResponse -> Response(
                        description = when {
                            response.description != null -> response.description
                            else -> response.statusCode.description
                        },
                        produces = listOf(it.contentType.run { "$contentType/$contentSubtype" })
                    )
                }
            }
        }

        fun create(
            httpStatusCode: HttpStatusCode,
            typeInfo: TypeInfo,
            description: String?
        ): Response {
            return Response(
                description = when {
                    description != null -> description
                    typeInfo.type == Unit::class -> httpStatusCode.description
                    else -> typeInfo.responseDescription()
                },
                schema = if (typeInfo.type == Unit::class) null else ModelOrModelReference.create(
                    "#/definitions/" + typeInfo.modelName()
                )
            )
        }

        fun create(
            modelName: String
        ): Response {
            return Response(
                description = modelName,
                schema = ModelOrModelReference.create("#/definitions/" + modelName)
            )
        }
    }
}

class Operation(
    override val responses: Map<HttpStatus, ResponseBase>,
    override val parameters: List<ParameterBase>,
    override val tags: List<Tag>?,
    override val summary: String,
    override val description: String?,
    override val security: List<Map<String, List<String>>>?
) : OperationBase {

    companion object : OperationCreator {
        override fun create(
            responses: Map<HttpStatus, ResponseBase>,
            parameters: List<ParameterBase>,
            tags: List<Tag>?,
            summary: String,
            description: String?,
            examples: Map<String, Example>,
            security: List<Map<String, List<String>>>?
        ): OperationBase {
            return Operation(
                responses,
                parameters,
                tags,
                summary,
                description,
                security
            )
        }
    }
}

class Parameter(
    override val name: String,
    override val `in`: ParameterInputType,
    override val description: String?,
    override val required: Boolean,
    val type: String? = null,
    val format: String? = null,
    val enum: List<String>? = null,
    val items: Property? = null,
    val default: String? = null,
    val schema: ModelOrModelReference? = null
) : ParameterBase {
    companion object : ParameterCreator {

        override fun create(
            property: Property,
            name: String,
            `in`: ParameterInputType,
            description: String?,
            required: Boolean,
            default: String?,
            examples: Map<String, Example>
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
                default = default,
                schema = property.`$ref`?.let { ModelOrModelReference(it) }
            )
        }
    }
}
