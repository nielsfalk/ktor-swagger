package de.nielsfalk.playground.ktor.swagger

import com.winterbe.expekt.should
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.gson.GsonSupport
import org.jetbrains.ktor.http.ContentType
import org.jetbrains.ktor.http.HttpMethod.Companion.Get
import org.jetbrains.ktor.request.accept
import org.jetbrains.ktor.testing.handleRequest
import org.jetbrains.ktor.testing.withTestApplication
import org.junit.Test

/**
 * @author Niels Falk
 */
class SwaggerUiTest {
    @Test
    fun `installed apidocs`(): Unit = withTestApplication {
        //when
        application.install(SwaggerUi) { forwardRoot = true }

        //then
        handleRequest(Get, "/").response.headers.get("Location").should.equal("apidocs")
        handleRequest(Get, "/apidocs").response.headers.get("Location").should.equal("apidocs/index.html?url=swagger.json")
        handleRequest(Get, "/apidocs/").response.headers.get("Location").should.equal("apidocs/index.html?url=swagger.json")
    }

    @Test
    fun `provide webjar`(): Unit = withTestApplication {
        //when
        application.install(SwaggerUi) { forwardRoot = true }

        //then
        handleRequest(Get, "/apidocs/index.html").response.content.should.contain("<title>Swagger UI</title>")
    }

    @Test
    fun `provide swaggerJson`(): Unit = withTestApplication {
        //when
        application.install(GsonSupport)
        application.install(SwaggerUi) { forwardRoot = true }

        //then
        handleRequest {
            uri = "/apidocs/swagger.json"
            method = Get
            addHeader("Accept", ContentType.Application.Json.toString())
        }.response.content.should.contain("{\"swagger\":\"2.0\"}")
    }
}