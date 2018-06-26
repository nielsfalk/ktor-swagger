package de.nielsfalk.playground.ktor.swagger

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.application.featureOrNull
import io.ktor.application.install
import io.ktor.pipeline.PipelineContext
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.util.AttributeKey


class SwaggerSupport() {
    companion object Feature : ApplicationFeature<Application, SwaggerUiConfiguration, SwaggerSupport> {
        override val key = AttributeKey<SwaggerSupport>("gson")

        override fun install(application: Application, configure: SwaggerUiConfiguration.() -> Unit): SwaggerSupport {
            val (path, forwardRoot, provideUi) = SwaggerUiConfiguration().apply(configure)
            val feature = SwaggerSupport()
            application.routing {
                get("/$path") {
                    redirect(path)
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
                        redirect(path)
                    }
                }
            }
            return feature
        }

        private suspend fun PipelineContext<Unit, ApplicationCall>.redirect(path: String) {
            call.respondRedirect("/$path/index.html?url=swagger.json")
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