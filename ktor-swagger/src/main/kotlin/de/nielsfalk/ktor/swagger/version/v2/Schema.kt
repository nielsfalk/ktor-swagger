package de.nielsfalk.ktor.swagger.version.v2

import de.nielsfalk.ktor.swagger.modelName
import de.nielsfalk.ktor.swagger.responseDescription
import de.nielsfalk.ktor.swagger.version.shared.CommonBase
import de.nielsfalk.ktor.swagger.version.shared.Information
import de.nielsfalk.ktor.swagger.version.shared.ModelName
import de.nielsfalk.ktor.swagger.version.shared.ModelReference
import de.nielsfalk.ktor.swagger.version.shared.Paths
import de.nielsfalk.ktor.swagger.version.shared.ResponseBase
import de.nielsfalk.ktor.swagger.version.shared.ResponseCreator
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
        override fun create(httpStatusCode: HttpStatusCode, typeInfo: TypeInfo): Response {
            return Response(
                description = if (typeInfo.type == Unit::class) httpStatusCode.description else typeInfo.responseDescription(),
                schema = if (typeInfo.type == Unit::class) null else ModelReference.create(
                    "#/definitions/" + typeInfo.modelName()
                )
            )
        }

        override fun create(modelName: String): Response {
            return Response(
                description = modelName,
                schema = ModelReference.create("#/definitions/" + modelName)
            )
        }
    }
}
