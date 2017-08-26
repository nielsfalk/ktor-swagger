package de.nielsfalk.playground.ktor.swagger

import org.jetbrains.ktor.application.ApplicationCall
import org.jetbrains.ktor.content.FinalContent
import org.jetbrains.ktor.http.ContentType
import org.jetbrains.ktor.http.ContentType.Application.JavaScript
import org.jetbrains.ktor.http.ContentType.Image.PNG
import org.jetbrains.ktor.http.ContentType.Text.CSS
import org.jetbrains.ktor.http.ContentType.Text.Html
import org.jetbrains.ktor.http.withCharset
import org.jetbrains.ktor.response.contentLength
import org.jetbrains.ktor.response.contentType
import org.jetbrains.ktor.response.respond
import org.jetbrains.ktor.util.ValuesMap
import java.net.URL

/**
 * @author Niels Falk
 */
class SwaggerUi {
    private val notFound = mutableListOf<String>()
    private val content = mutableMapOf<String, ResourceContent>()
    suspend fun serve(filename: String?, call: ApplicationCall) {
        when (filename) {
            in notFound -> return
            null -> return
            else -> {
                val resource = this::class.java.getResource("/META-INF/resources/webjars/swagger-ui/3.1.5/$filename")
                if (resource == null) {
                    notFound.add(filename)
                    return
                }
                call.respond(content.getOrPut(filename) { ResourceContent(resource) })
            }
        }
    }
}

private val contentTypes = mapOf(
        "html" to Html,
        "css" to CSS,
        "js" to JavaScript,
        "json" to ContentType.Application.Json.withCharset(Charsets.UTF_8),
        "png" to PNG)

private class ResourceContent(val resource: URL) : FinalContent.ByteArrayContent() {
    private val bytes by lazy { resource.readBytes() }

    override val headers by lazy {
        ValuesMap.build(caseInsensitiveKey = true) {
            val extension = resource.file.substring(resource.file.lastIndexOf('.') + 1)
            contentType(contentTypes[extension] ?: Html)
            contentLength(bytes.size.toLong())
        }
    }

    override fun bytes(): ByteArray = bytes
    override fun toString() = "ResourceContent \"$resource\""
}
