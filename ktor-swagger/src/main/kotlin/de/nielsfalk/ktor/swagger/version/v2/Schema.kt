package de.nielsfalk.ktor.swagger.version.v2

import de.nielsfalk.ktor.swagger.version.shared.CommonBase
import de.nielsfalk.ktor.swagger.version.shared.Information
import de.nielsfalk.ktor.swagger.version.shared.ModelName
import de.nielsfalk.ktor.swagger.version.shared.Paths

typealias Definitions = MutableMap<ModelName, Any>

class Swagger : CommonBase {
    val swagger = "2.0"
    override var info: Information? = null
    override val paths: Paths = mutableMapOf()
    val definitions: Definitions = mutableMapOf()
}
