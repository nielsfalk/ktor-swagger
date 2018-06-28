package de.nielsfalk.playground.ktor.swagger

import io.ktor.application.install
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.pipeline.ContextDsl
import io.ktor.routing.Routing
import io.ktor.routing.routing
import io.ktor.server.testing.withTestApplication
import org.junit.Test

const val rectanglesLocation = "/toys"
@Location(rectanglesLocation)
class rectangles

const val ref = "${'$'}ref"

val sizeSchema = """
"size" : {
    "type" : "number",
    "minimum" : 0
}
""".trimIndent()

val rectangleSchema = """
"Rectangle" : {
    "type" : "object",
    "properties" : {
        "a" : {"$ref" : "#/definitions/size"},
        "b" : {"$ref" : "#/definitions/size"}
    }
}
""".trimIndent()

class SwaggerManualSchemaTest {
    private lateinit var swagger: Swagger

    @ContextDsl
    private fun applicationCustomRoute(configuration: Routing.() -> Unit) {
        withTestApplication({
            install(Locations)
            install(SwaggerSupport)
        }) {
            application.routing(configuration)
            this@SwaggerManualSchemaTest.swagger = application.swagger.swagger
        }
    }

    @Test
    fun `custom ok return type`() {
        applicationCustomRoute {
//            get<rectangles>("all".responds(ok(rectangleSchema)))
        }
    }
}
