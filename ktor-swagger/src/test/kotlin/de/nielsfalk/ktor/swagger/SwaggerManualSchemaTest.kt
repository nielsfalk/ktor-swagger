package de.nielsfalk.ktor.swagger

import com.winterbe.expekt.should
import de.nielsfalk.ktor.swagger.version.shared.ModelReference
import de.nielsfalk.ktor.swagger.version.shared.ParameterInputType
import de.nielsfalk.ktor.swagger.version.v2.Response as ResponseV2
import de.nielsfalk.ktor.swagger.version.v3.Response as ResponseV3
import de.nielsfalk.ktor.swagger.version.v2.Swagger
import de.nielsfalk.ktor.swagger.version.v3.OpenApi
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

val rectanglesSchemaMap = mapOf(
    "type" to "array",
    "items" to mapOf(
        "description" to "Rectangles",
        ref to "#/definitions/Rectangle"
    )
)

data class Rectangle(
    val a: Int,
    val b: Int
)

class SwaggerManualSchemaTest {
    private lateinit var swagger: Swagger
    private lateinit var openApi: OpenApi

    @ContextDsl
    private fun applicationCustomRoute(configuration: Routing.() -> Unit) {
        withTestApplication({
            install(Locations)
            install(SwaggerSupport) {
                swagger = Swagger().apply { definitions["size"] = sizeSchemaMap }
                openApi = OpenApi().apply { components.schemas["size"] = sizeSchemaMap }
            }
        }) {
            application.routing(configuration)
            this@SwaggerManualSchemaTest.swagger = application.swagger.swagger!!
            this@SwaggerManualSchemaTest.openApi = application.swagger.openApi!!
        }
    }

    @Test
    fun `custom ok return type`() {
        applicationCustomRoute {
            get<rectangles>(
                "all".responds(
                    ok(
                        "Rectangle",
                        rectangleSchemaMap
                    )
                )
            ) { }
        }
        swagger.definitions["size"].should.equal(sizeSchemaMap)
        openApi.components.schemas["size"].should.equal(sizeSchemaMap)
        swagger.definitions["Rectangle"].should.equal(rectangleSchemaMap)
        openApi.components.schemas["Rectangle"].should.equal(rectangleSchemaMap)
    }

    @Test
    fun `custom put schema`() {
        applicationCustomRoute {
            put<rectangles, Rectangle>("create".body(rectangleSchemaMap).responds(
                    created("Rectangles", rectanglesSchemaMap)
                )
            ) { _, _ ->
            }
        }
        swagger.definitions["Rectangle"].should.equal(rectangleSchemaMap)
        openApi.components.schemas["Rectangle"].should.equal(rectangleSchemaMap)
        swagger.definitions["Rectangles"].should.equal(rectanglesSchemaMap)
        openApi.components.schemas["Rectangles"].should.equal(rectanglesSchemaMap)
        swagger.paths[rectanglesLocation]?.get("put").apply {
            should.not.be.`null`
        }?.also { operation ->
            operation.summary.should.equal("create")
            operation.responses.keys.should.contain("201")
            (operation.parameters.find { it.`in` == ParameterInputType.body }?.schema as ModelReference)
                .`$ref`.should.equal("#/definitions/Rectangle")
            (operation.responses["201"] as ResponseV2).schema?.`$ref`.should.equal("#/definitions/Rectangles")
        }
        openApi.paths[rectanglesLocation]?.get("put").apply {
            should.not.be.`null`
        }?.also { operation ->
            operation.summary.should.equal("create")
            operation.responses.keys.should.contain("201")
            (operation.parameters.find { it.`in` == ParameterInputType.body }?.schema as ModelReference)
                .`$ref`.should.equal("#/components/schemas/Rectangle")
            (operation.responses["201"] as ResponseV3)
                .content?.get("application/json")?.schema?.`$ref`.should.equal("#/components/schemas/Rectangles")
        }
    }

    @Test
    fun `custom schema name on the receive type`() {
        val customName = "CustomName"
        applicationCustomRoute {
            post<rectangles, Rectangle>("create".body(customName, rectangleSchemaMap).responds(
                    created("Rectangles", rectanglesSchemaMap)
                )
            ) { _, _ ->
            }
        }
        swagger.definitions[customName].should.equal(rectangleSchemaMap)
        openApi.components.schemas[customName].should.equal(rectangleSchemaMap)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `body in get throws exception`() {
        applicationCustomRoute {
            get<rectangles>("Get All".body("")) {}
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `body in delete throws exception`() {
        applicationCustomRoute {
            delete<rectangles>("Delete All".body("")) {}
        }
    }
}
