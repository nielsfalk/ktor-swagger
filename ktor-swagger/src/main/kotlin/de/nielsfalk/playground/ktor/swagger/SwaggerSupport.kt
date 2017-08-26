package de.nielsfalk.playground.ktor.swagger

import org.jetbrains.ktor.application.Application
import org.jetbrains.ktor.application.ApplicationFeature
import org.jetbrains.ktor.application.featureOrNull
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.response.respond
import org.jetbrains.ktor.response.respondRedirect
import org.jetbrains.ktor.routing.Routing
import org.jetbrains.ktor.routing.get
import org.jetbrains.ktor.util.AttributeKey

/**
 * @author Niels Falk
 */


class SwaggerSupport() {
    companion object Feature : ApplicationFeature<Application, SwaggerUiConfiguration, SwaggerSupport> {
        override val key = AttributeKey<SwaggerSupport>("gson")

        override fun install(application: Application, configure: SwaggerUiConfiguration.() -> Unit): SwaggerSupport {
            val (path, forwardRoot, provideUi) = SwaggerUiConfiguration().apply(configure)
            val feature = SwaggerSupport()
            application.routing {
                get("/$path") {
                    call.respondRedirect("$path/index.html?url=swagger.json")
                }
                val ui = if (provideUi) SwaggerUi() else null
                get("/$path/{fileName}") {
                    val filename = call.parameters["fileName"]
                    if (filename == "swagger.json") {
                        call.respond(swagger)
                    } else {
                        ui?.serve(filename, call)
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

data class SwaggerUiConfiguration(
        var path: String = "apidocs",
        var forwardRoot: Boolean = false,
        var provideUi: Boolean = true
)

fun Application.routing(configure: Routing.() -> Unit) =
        featureOrNull(Routing)?.apply(configure)
                ?: install(Routing, configure)