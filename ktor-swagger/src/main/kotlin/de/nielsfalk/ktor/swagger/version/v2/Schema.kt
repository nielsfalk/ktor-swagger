package de.nielsfalk.ktor.swagger.version.v2

import de.nielsfalk.ktor.swagger.modelName
import de.nielsfalk.ktor.swagger.responseDescription
import de.nielsfalk.ktor.swagger.version.shared.CommonBase
import de.nielsfalk.ktor.swagger.version.shared.HttpStatus
import de.nielsfalk.ktor.swagger.version.shared.Information
import de.nielsfalk.ktor.swagger.version.shared.ModelName
import de.nielsfalk.ktor.swagger.version.shared.ModelReference
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
    val schema: ModelReference?
) : ResponseBase {

    companion object : ResponseCreator {
        override fun create(
            httpStatusCode: HttpStatusCode,
            typeInfo: TypeInfo,
            examples: Map<String, Example>
        ): Response {
            return Response(
                description = if (typeInfo.type == Unit::class) httpStatusCode.description else typeInfo.responseDescription(),
                schema = if (typeInfo.type == Unit::class) null else ModelReference.create(
                    "#/definitions/" + typeInfo.modelName()
                )
            )
        }

        override fun create(
            modelName: String,
            examples: Map<String, Example>
        ): Response {
            return Response(
                description = modelName,
                schema = ModelReference.create("#/definitions/" + modelName)
            )
        }
    }
}

class Operation(
    override val responses: Map<HttpStatus, ResponseBase>,
    override val parameters: List<ParameterBase>,
    override val tags: List<Tag>?,
    override val summary: String,
    override val description: String?
) : OperationBase {

    companion object : OperationCreator {
        override fun create(
            responses: Map<HttpStatus, ResponseBase>,
            parameters: List<ParameterBase>,
            tags: List<Tag>?,
            summary: String,
            description: String?,
            examples: Map<String, Example>
        ): OperationBase {
            return Operation(
                responses,
                parameters,
                tags,
                summary,
                description
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
    val schema: ModelReference? = null
) : ParameterBase {
    companion object : ParameterCreator {

        override fun create(
            property: Property,
            name: String,
            `in`: ParameterInputType,
            description: String?,
            required: Boolean,
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
                schema = property.`$ref`?.let { ModelReference(it) }
            )
        }
    }
}
