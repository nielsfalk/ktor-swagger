package de.nielsfalk.ktor.swagger

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Image.PNG
import io.ktor.http.ContentType.Text.CSS
import io.ktor.http.ContentType.Text.Html
import io.ktor.http.ContentType.Text.JavaScript
import io.ktor.http.content.OutgoingContent
import io.ktor.http.withCharset
import io.ktor.response.respond
import java.net.URL

class SwaggerUi {
    private val notFound = mutableListOf<String>()
    private val content = mutableMapOf<String, ResourceContent>()
    suspend fun serve(filename: String?, call: ApplicationCall) {
        when (filename) {
            in notFound -> return
            null -> return
            else -> {
                val resource = this::class.java.getResource("/META-INF/resources/webjars/swagger-ui/3.23.8/$filename")
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

private class ResourceContent(val resource: URL) : OutgoingContent.ByteArrayContent() {
    private val bytes by lazy { resource.readBytes() }

    override val contentType: ContentType? by lazy {
        val extension = resource.file.substring(resource.file.lastIndexOf('.') + 1)
        contentTypes[extension] ?: Html
    }

    override val contentLength: Long? by lazy {
        bytes.size.toLong()
    }

    override fun bytes(): ByteArray = bytes
    override fun toString() = "ResourceContent \"$resource\""
}
