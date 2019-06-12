package de.nielsfalk.ktor.swagger

import com.winterbe.expekt.should
import de.nielsfalk.ktor.swagger.version.v3.OpenApi
import io.ktor.application.install
import io.ktor.http.ContentType
import io.ktor.locations.Locations
import io.ktor.routing.routing
import io.ktor.server.testing.withTestApplication
import org.junit.Before
import org.junit.Test

class SecurityTest {
    private lateinit var openapi: OpenApi

    @Before
    fun setUp() {
        withTestApplication({
            install(Locations)
            install(SwaggerSupport) {
                openApi = OpenApi().apply {
                    this.security = listOf(mapOf("basic" to listOf()))
                    components.securitySchemes["basic"] = mapOf(
                            "type" to "http",
                            "scheme" to "basic"
                    )
                    components.securitySchemes["basic2"] = mapOf(
                            "type" to "http",
                            "scheme" to "basic"
                    )
                }
                openApiCustomization = {
                    responds(
                            internalServerError<ErrorModel>()
                    )
                }
            }
        }) {
            // when:
            application.routing {
                get<toy>(
                        "image"
                                .description("A single toy, also returns image of toy for correct mime type")
                                .responds(
                                        ok(
                                                json<ToyModel>(),
                                                contentTypeResponse(ContentType.Image.PNG),
                                                description = ""
                                        )
                                )
                ) {
                }
                get<toys>(
                        "all"
                                .responds(
                                        ok<ToysModel>(example("model", ToysModel.example)),
                                        notFound()
                                ).security(mapOf("basic2" to emptyList()))
                ) { }
            }

            this@SecurityTest.openapi = application.swaggerUi.openApi!!
        }
    }

    @Test
    fun `component security scheme should have all values defined`() {
        // The securitySchemas define what security is exposed in swagger.
        // This is a bit of a silly test as we are just checking the values we defined in the setup
        // This is defined here https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#securitySchemeObject
        openapi.components.securitySchemes.should.equal(mapOf(
                "basic" to mapOf(
                "type" to "http",
                "scheme" to "basic"
                ),
                "basic2" to mapOf(
                        "type" to "http",
                        "scheme" to "basic"
                )

        ))

        // Security on the open api requires all operations to be authenticate using the basic auth schema
        // This is defined here: https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#securityRequirementObject
        openapi.security.should.equal(listOf(mapOf("basic" to listOf())))
    }

    @Test
    fun `operation should have security defined on it`() {
        // And an operation can have a
        // This is defined here: https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#securityRequirementObject
        openapi.paths.get(toyLocation)?.get("get")!!.security.should.equal(listOf(mapOf("basic2" to emptyList())))
    }
}
