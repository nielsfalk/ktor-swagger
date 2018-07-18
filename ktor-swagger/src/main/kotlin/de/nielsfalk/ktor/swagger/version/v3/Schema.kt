package de.nielsfalk.ktor.swagger.version.v3

import de.nielsfalk.ktor.swagger.modelName
import de.nielsfalk.ktor.swagger.responseDescription
import de.nielsfalk.ktor.swagger.version.shared.CommonBase
import de.nielsfalk.ktor.swagger.version.shared.HttpStatus
import de.nielsfalk.ktor.swagger.version.shared.Information
import de.nielsfalk.ktor.swagger.version.shared.ModelReference
import de.nielsfalk.ktor.swagger.version.shared.OperationBase
import de.nielsfalk.ktor.swagger.version.shared.OperationCreator
import de.nielsfalk.ktor.swagger.version.shared.ParameterBase
import de.nielsfalk.ktor.swagger.version.shared.ParameterCreator
import de.nielsfalk.ktor.swagger.version.shared.ParameterInputType
import de.nielsfalk.ktor.swagger.version.shared.Paths
import de.nielsfalk.ktor.swagger.version.shared.Property
import de.nielsfalk.ktor.swagger.version.shared.RefHolder
import de.nielsfalk.ktor.swagger.version.shared.ResponseBase
import de.nielsfalk.ktor.swagger.version.shared.ResponseCreator
import de.nielsfalk.ktor.swagger.version.shared.Tag
import io.ktor.client.call.TypeInfo
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
        override fun create(
            httpStatusCode: HttpStatusCode,
            typeInfo: TypeInfo,
            examples: Map<String, Example>
        ): Response {
            val jsonContent = if (typeInfo.type == Unit::class) null else ModelReference.create(
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

        override fun create(
            modelName: String,
            examples: Map<String, Example>
        ): Response {
            return Response(
                description = modelName,
                content = mapOf(
                    "application/json" to
                        MediaTypeObject(
                            ModelReference.create("#/components/schemas/" + modelName),
                            example = examples.values.firstOrNull()?.value,
                            examples = examples
                        )
                )
            )
        }
    }
}

class MediaTypeObject(
    val schema: ModelReference,
    val example: Any? = null,
    val examples: Map<String, Example> = mapOf()
)

class Operation(
    override val responses: Map<HttpStatus, ResponseBase>,
    override val parameters: List<Parameter>,
    override val tags: List<Tag>?,
    override val summary: String,
    val requestBody: RequestBody?
) : OperationBase {

    companion object : OperationCreator {
        override fun create(
            responses: Map<HttpStatus, ResponseBase>,
            parameters: List<ParameterBase>,
            tags: List<Tag>?,
            summary: String,
            examples: Map<String, Example>
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
                    val content = mapOf(
                        "application/json" to MediaTypeObject(
                            ModelReference(
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
                requestBody = requestBody
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
    val schema: RefHolder,
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
            examples: Map<String, Example>
        ): ParameterBase {
            return Parameter(
                name = name,
                `in` = `in`,
                description = description,
                required = required,
                schema = property,
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
