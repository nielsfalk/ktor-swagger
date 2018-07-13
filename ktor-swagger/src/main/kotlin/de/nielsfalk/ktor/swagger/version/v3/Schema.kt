package de.nielsfalk.ktor.swagger.version.v3

import de.nielsfalk.ktor.swagger.version.shared.CommonBase
import de.nielsfalk.ktor.swagger.version.shared.Information
import de.nielsfalk.ktor.swagger.version.shared.Paths

typealias Schemas = MutableMap<String, Any>
typealias Responses = MutableMap<String, Any>
typealias Parameters = MutableMap<String, Any>
typealias Examples = MutableMap<String, Any>
typealias RequestBodies = MutableMap<String, Any>
typealias Headers = MutableMap<String, Any>
typealias SecuritySchemes = MutableMap<String, Any>
typealias Links = MutableMap<String, Any>
typealias Callbacks = MutableMap<String, Any>

class OpenApi : CommonBase {
    val openapi: String = "3.0.0"

    override var info: Information? = null
    override val paths: Paths = mutableMapOf()

    val components: Components = Components()
}

class Components {
    val schemas: Schemas = mutableMapOf()

    val responses: Responses = mutableMapOf()

    val paramers: Parameters = mutableMapOf()

    val examples: Examples = mutableMapOf()

    val requestBodies: RequestBodies = mutableMapOf()

    val headers: Headers = mutableMapOf()

    val securitySchemes: SecuritySchemes = mutableMapOf()

    val links: Links = mutableMapOf()

    val callbacks: Callbacks = mutableMapOf()
}

@Target(AnnotationTarget.PROPERTY)
annotation class Schema(val schema: String)
