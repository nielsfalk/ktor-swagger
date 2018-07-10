package de.nielsfalk.ktor.swagger

import com.winterbe.expekt.should
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.GsonConverter
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.Test

class SwaggerSupportTest {
    @Test
    fun `installed apidocs`(): Unit = withTestApplication {
        // when
        application.install(SwaggerSupport) { forwardRoot = true }

        // then
        handleRequest(
            HttpMethod.Get,
            "/"
        ).response.headers["Location"].should.equal("/apidocs/index.html?url=swagger.json")
        handleRequest(
            HttpMethod.Get,
            "/apidocs"
        ).response.headers["Location"].should.equal("/apidocs/index.html?url=swagger.json")
        handleRequest(
            HttpMethod.Get,
            "/apidocs/"
        ).response.headers["Location"].should.equal("/apidocs/index.html?url=swagger.json")
    }

    @Test
    fun `provide webjar`(): Unit = withTestApplication {
        // when
        application.install(SwaggerSupport) { forwardRoot = true }

        // then
        handleRequest(
            HttpMethod.Get,
            "/apidocs/index.html"
        ).response.content.should.contain("<title>Swagger UI</title>")
    }

    @Test
    fun `provide swaggerJson`(): Unit = withTestApplication {
        // when
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, GsonConverter())
        }
        application.install(SwaggerSupport) { forwardRoot = true }

        // then
        handleRequest {
            uri = "/apidocs/swagger.json"
            method = HttpMethod.Get
            addHeader("Accept", ContentType.Application.Json.toString())
        }.response.content.should.contain("\"swagger\":\"2.0\"")
    }

    @Location("/model")
    private class modelRoute

    private class Model(val value: String)

    @Test
    fun `provide swaggerJson when a custom schema is provided`(): Unit = withTestApplication {
        // when
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, GsonConverter())
        }
        application.install(SwaggerSupport) {
            forwardRoot = true
        }
        application.install(Locations)

        application.routing {
            put<modelRoute, Model>(body(mapOf("type" to "object"))) { _, _ -> }
        }

        // then
        handleRequest {
            uri = "/apidocs/swagger.json"
            method = HttpMethod.Get
            addHeader("Accept", ContentType.Application.Json.toString())
        }.response.content.should.contain("\"swagger\":\"2.0\"").and.contain("\"type\":\"object\"")
    }
}
