package de.nielsfalk.ktor.swagger.version.v3

import de.nielsfalk.ktor.swagger.modelName
import de.nielsfalk.ktor.swagger.responseDescription
import de.nielsfalk.ktor.swagger.version.shared.CommonBase
import de.nielsfalk.ktor.swagger.version.shared.HttpStatus
import de.nielsfalk.ktor.swagger.version.shared.Information
import de.nielsfalk.ktor.swagger.version.shared.ModelReference
import de.nielsfalk.ktor.swagger.version.shared.OperationBase
import de.nielsfalk.ktor.swagger.version.shared.OperationCreator
import de.nielsfalk.ktor.swagger.version.shared.Parameter
import de.nielsfalk.ktor.swagger.version.shared.ParameterInputType
import de.nielsfalk.ktor.swagger.version.shared.Paths
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

typealias Content = Map<String, SchemaModelReference>

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
        override fun create(httpStatusCode: HttpStatusCode, typeInfo: TypeInfo): Response {
            val jsonContent = if (typeInfo.type == Unit::class) null else ModelReference.create(
                "#/components/schemas/" + typeInfo.modelName()
            )
            val content = jsonContent?.let { mapOf("application/json" to SchemaModelReference(it)) }
            return Response(
                description = if (typeInfo.type == Unit::class) httpStatusCode.description else typeInfo.responseDescription(),
                content = content
            )
        }

        override fun create(modelName: String): Response {
            return Response(
                description = modelName,
                content = mapOf(
                    "application/json" to
                        SchemaModelReference(ModelReference.create("#/components/schemas/" + modelName))
                )
            )
        }
    }
}

class SchemaModelReference(
    val schema: ModelReference
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
            parameters: List<Parameter>,
            tags: List<Tag>?,
            summary: String
        ): OperationBase {
            val bodyParams =
                parameters
                    .filter { it.`in` == ParameterInputType.body }

            assert(bodyParams.size < 2) {
                "Should not be more than 1 noReflectionBody parameter."
            }

            val parametersToUse =
                parameters
                    .filter { it.`in` != ParameterInputType.body }

            val requestBody: RequestBody? =
                bodyParams.firstOrNull()?.let {
                val content = mapOf(
                    "application/json" to SchemaModelReference(
                        ModelReference(
                            `$ref` = it.schema!!.`$ref`
                        )
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

class RequestBody(
    val content: Content
)

@Target(AnnotationTarget.PROPERTY)
annotation class Schema(val schema: String)
