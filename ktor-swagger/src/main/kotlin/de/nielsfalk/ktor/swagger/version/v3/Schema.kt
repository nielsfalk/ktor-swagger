package de.nielsfalk.ktor.swagger.version.v3

import de.nielsfalk.ktor.swagger.CustomContentTypeResponse
import de.nielsfalk.ktor.swagger.HttpCodeResponse
import de.nielsfalk.ktor.swagger.JsonResponseFromReflection
import de.nielsfalk.ktor.swagger.JsonResponseSchema
import de.nielsfalk.ktor.swagger.modelName
import de.nielsfalk.ktor.swagger.responseDescription
import de.nielsfalk.ktor.swagger.version.shared.CommonBase
import de.nielsfalk.ktor.swagger.version.shared.HttpStatus
import de.nielsfalk.ktor.swagger.version.shared.Information
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
import io.ktor.client.call.TypeInfo
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode

typealias Schemas = MutableMap<String, Any>
typealias Responses = MutableMap<String, Any>
typealias Parameters = MutableMap<String, Any>
typealias Examples = MutableMap<String, Any>
typealias RequestBodies = MutableMap<String, Any>
typealias Headers = MutableMap<String, Any>
typealias SecuritySchemes = MutableMap<String, Any>
typealias Links = MutableMap<String, Any>
typealias Callbacks = MutableMap<String, Any>

typealias Content = Map<String, MediaTypeObject>

class OpenApi : CommonBase {
    val openapi: String = "3.0.0"

    override var info: Information? = null
    override val paths: Paths = mutableMapOf()

    val components: Components = Components()

    var security: List<Map<String, List<String>>>? = null
}

class Components {
    val schemas: Schemas = mutableMapOf()

    val responses: Responses = mutableMapOf()

    val parameters: Parameters = mutableMapOf()

    val examples: Examples = mutableMapOf()

    val requestBodies: RequestBodies = mutableMapOf()

    val headers: Headers = mutableMapOf()

    val securitySchemes: SecuritySchemes = mutableMapOf()

    val links: Links = mutableMapOf()

    val callbacks: Callbacks = mutableMapOf()
}

class Response(
    override val description: String,
    val content: Content?
) : ResponseBase {

    companion object : ResponseCreator {
        override fun create(response: HttpCodeResponse): ResponseBase? {
            val singleResponseOrNull = response.responseTypes.singleOrNull()
            val description = response.description ?: singleResponseOrNull
                ?.let { it as? JsonResponseFromReflection }
                ?.let { it.type.responseDescription() } ?: singleResponseOrNull
                ?.let { it as? JsonResponseSchema }
                ?.name ?: response.statusCode.description

            val content = response
                .responseTypes
                .map {
                    when (it) {
                        is JsonResponseSchema -> createContent(it)
                        is JsonResponseFromReflection -> createContent(it)
                        is CustomContentTypeResponse -> createContent(it)
                    }
                }
                .filterNotNull()
                .toMap()

            return Response(
                description = description,
                content = content
            )
        }

        private fun createContent(jsonResponseSchema: JsonResponseSchema) =
            jsonResponseSchema.run {
                "application/json" to
                    MediaTypeObject(
                        ModelOrModelReference.create("#/components/schemas/" + name),
                        example = examples.values.firstOrNull()?.value,
                        examples = examples
                    )
            }

        private fun createContent(jsonResponseFromReflection: JsonResponseFromReflection) =
            jsonResponseFromReflection.run {
                val jsonContent = if (type.type == Unit::class) null else ModelOrModelReference.create(
                    "#/components/schemas/" + type.modelName()
                )
                jsonContent?.let {
                    "application/json" to MediaTypeObject(
                        it,
                        example = examples.values.firstOrNull()?.value,
                        examples = examples
                    )
                }
            }

        private fun createContent(customContentTypeResponse: CustomContentTypeResponse) =
            customContentTypeResponse.run {
                val contentTypeString = contentType.run { "$contentType/$contentSubtype" }
                val modelOrModelReference = when {
                    contentType.match(ContentType.Image.Any) -> ModelOrModelReference.create(
                        type = "string",
                        format = "binary"
                    )
                    contentType.match(ContentType.Text.Any) -> ModelOrModelReference.create(
                        type = "string"
                    )
                    else -> throw UnsupportedOperationException(
                        "Unsupported automatic module assignment for ContentType $contentTypeString"
                    )
                }
                contentTypeString to MediaTypeObject(
                    schema = modelOrModelReference
                )
            }

        private fun create(
            httpStatusCode: HttpStatusCode,
            typeInfo: TypeInfo,
            examples: Map<String, Example>
        ): Response {
            val jsonContent = if (typeInfo.type == Unit::class) null else ModelOrModelReference.create(
                "#/components/schemas/" + typeInfo.modelName()
            )
            val content = jsonContent?.let {
                mapOf(
                    "application/json" to MediaTypeObject(
                        it,
                        example = examples.values.firstOrNull()?.value,
                        examples = examples
                    )
                )
            }
            return Response(
                description = if (typeInfo.type == Unit::class) httpStatusCode.description else typeInfo.responseDescription(),
                content = content
            )
        }

        private fun create(
            modelName: String,
            examples: Map<String, Example>
        ): Response {
            return Response(
                description = modelName,
                content = mapOf(
                    "application/json" to
                        MediaTypeObject(
                            ModelOrModelReference.create("#/components/schemas/" + modelName),
                            example = examples.values.firstOrNull()?.value,
                            examples = examples
                        )
                )
            )
        }
    }
}

class MediaTypeObject(
    val schema: ModelOrModelReference,
    val example: Any? = null,
    val examples: Map<String, Example> = mapOf()
)

class Operation(
    override val responses: Map<HttpStatus, ResponseBase>,
    override val parameters: List<Parameter>,
    override val tags: List<Tag>?,
    override val summary: String,
    override val description: String?,
    override val security: List<Map<String, List<String>>>?,
    override val operationId: String?,
    val requestBody: RequestBody?
) : OperationBase {

    companion object : OperationCreator {
        override fun create(
            responses: Map<HttpStatus, ResponseBase>,
            parameters: List<ParameterBase>,
            tags: List<Tag>?,
            summary: String,
            description: String?,
            examples: Map<String, Example>,
            security: List<Map<String, List<String>>>?,
            operationId: String?
        ): OperationBase {
            val bodyParams =
                parameters
                    .filter { it.`in` == ParameterInputType.body }.map { it as Parameter }

            assert(bodyParams.size < 2) {
                "Should not be more than 1 body parameter."
            }

            val parametersToUse =
                parameters
                    .filter { it.`in` != ParameterInputType.body }.map { it as Parameter }

            val requestBody: RequestBody? =
                bodyParams.firstOrNull()?.let {
                    val content = if (it.schema.type == "string") mapOf(
                            "text/plain" to MediaTypeObject(
                                    ModelOrModelReference(
                                            type = "string"
                                    ),
                                    example = examples.values.firstOrNull()?.value,
                                    examples = examples
                            )
                    )
                    else mapOf(
                        "application/json" to MediaTypeObject(
                            ModelOrModelReference(
                                `$ref` = it.schema.`$ref`!!
                            ),
                            example = examples.values.firstOrNull()?.value,
                            examples = examples
                        )
                    )
                    RequestBody(
                        content = content
                    )
                }

            return Operation(
                responses,
                parametersToUse,
                tags,
                summary,
                description,
                security,
                requestBody = requestBody,
                operationId = operationId
            )
        }
    }
}

class Parameter(
    override val name: String,
    override val `in`: ParameterInputType,
    override val description: String?,
    override val required: Boolean,
    val deprecated: Boolean = false,
    val allowEmptyValue: Boolean = true,
    val schema: Property,
    val example: Any? = null,
    val examples: Map<String, Example>? = null
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
        ): ParameterBase {
            return Parameter(
                name = name,
                `in` = `in`,
                description = description,
                required = required,
                schema = property.copy(default = default),
                examples = examples
            )
        }
    }
}

class RequestBody(
    val content: Content
)

data class Example(
    val summary: String?,
    val description: String?,
    val value: Any?,
    val externalValue: String?,
    val `$ref`: String?
)

@Target(AnnotationTarget.PROPERTY)
annotation class Schema(val schema: String)
