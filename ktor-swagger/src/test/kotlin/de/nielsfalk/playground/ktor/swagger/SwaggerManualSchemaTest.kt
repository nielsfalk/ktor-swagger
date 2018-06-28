package de.nielsfalk.playground.ktor.swagger

import com.winterbe.expekt.should
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

val sizeSchemaMap = mapOf(
    "type" to "number",
    "minimum" to 0
)

val rectangleSchemaMap = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "a" to mapOf(ref to "#/definitions/size"),
        "b" to mapOf(ref to "#/definitions/size")
    )
)

class SwaggerManualSchemaTest {
    private lateinit var swagger: Swagger

    @ContextDsl
    private fun applicationCustomRoute(configuration: Routing.() -> Unit) {
        withTestApplication({
            install(Locations)
            install(SwaggerSupport) {
                swagger.definitions["size"] = sizeSchemaMap
            }
        }) {
            application.routing(configuration)
            this@SwaggerManualSchemaTest.swagger = application.swagger.swagger
        }
    }

    @Test
    fun `custom ok return type`() {
        applicationCustomRoute {
            get<rectangles>("all".responds(ok("Rectangle", rectangleSchemaMap))) { }
        }
        swagger.definitions["size"].should.equal(sizeSchemaMap)
        swagger.definitions["Rectangle"].should.equal(rectangleSchemaMap)
    }
}
