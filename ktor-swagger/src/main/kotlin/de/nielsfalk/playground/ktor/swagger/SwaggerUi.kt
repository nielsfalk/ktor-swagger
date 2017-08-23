package de.nielsfalk.playground.ktor.swagger

import org.jetbrains.ktor.application.Application
import org.jetbrains.ktor.application.ApplicationFeature
import org.jetbrains.ktor.application.featureOrNull
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.content.FinalContent
import org.jetbrains.ktor.http.ContentType
import org.jetbrains.ktor.http.ContentType.Application.JavaScript
import org.jetbrains.ktor.http.ContentType.Image.PNG
import org.jetbrains.ktor.http.ContentType.Text.Html
import org.jetbrains.ktor.http.withCharset
import org.jetbrains.ktor.response.contentLength
import org.jetbrains.ktor.response.contentType
import org.jetbrains.ktor.response.respond
import org.jetbrains.ktor.response.respondRedirect
import org.jetbrains.ktor.routing.Routing
import org.jetbrains.ktor.routing.get
import org.jetbrains.ktor.util.AttributeKey
import org.jetbrains.ktor.util.ValuesMap.Companion.build
import java.net.URL

/**
 * @author Niels Falk
 */

private val notFound = mutableListOf<String>()
private val content = mutableMapOf<String, ResourceContent>()


class SwaggerUi {
    companion object Feature : ApplicationFeature<Application, SwaggerUiConfiguration, SwaggerUi> {
        override val key = AttributeKey<SwaggerUi>("gson")

        override fun install(application: Application, configure: SwaggerUiConfiguration.() -> Unit): SwaggerUi {
            val feature = SwaggerUi()
            val (path, forwardRoot) = SwaggerUiConfiguration().apply(configure)
            application.routing {
                get("/$path") {
                    call.respondRedirect("$path/index.html?url=swagger.json")
                }
                get("/$path/{fileName}") {
                    val filename = call.parameters["fileName"]
                    when (filename) {
                        "swagger.json" -> call.respond(swagger)
                        in notFound -> return@get
                        null -> return@get
                        else -> {
                            val resource = this::class.java.getResource("/META-INF/resources/webjars/swagger-ui/3.1.5/$filename")
                            if (resource == null) {
                                notFound.add(filename)
                                return@get
                            }
                            call.respond(content.getOrPut(filename) { ResourceContent(resource) })
                        }
                    }

                }
                if (forwardRoot) {
                    get("/") {
                        call.respondRedirect("apidocs")
                    }
                }
            }
            return feature
        }
    }
}

data class SwaggerUiConfiguration(var path: String = "apidocs", var forwardRoot: Boolean = false)

fun Application.routing(configure: Routing.() -> Unit) = featureOrNull(Routing)?.apply(configure) ?: install(Routing, configure)

private val contentTypes = mapOf(
        "html" to Html,
        "css" to ContentType.Text.CSS,
        "js" to JavaScript,
        "json" to ContentType.Application.Json.withCharset(Charsets.UTF_8),
        "png" to PNG)

private class ResourceContent(val resource: URL) : FinalContent.ByteArrayContent() {
    private val bytes by lazy { resource.readBytes() }

    override val headers by lazy {
        build(true) {
            val extension = resource.file.substring(resource.file.lastIndexOf('.') + 1)
            contentType(contentTypes[extension] ?: Html)
            contentLength(bytes.size.toLong())
        }
    }

    override fun bytes(): ByteArray = bytes
    override fun toString() = "ResourceContent \"$resource\""
}
