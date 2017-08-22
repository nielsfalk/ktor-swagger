package de.nielsfalk.playground.ktor.swagger

import com.winterbe.expekt.should
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.locations.Locations
import org.jetbrains.ktor.locations.location
import org.jetbrains.ktor.routing.routing
import org.jetbrains.ktor.testing.withTestApplication
import org.json.simple.JSONObject
import org.junit.Before
import org.junit.Test

data class ToyModel(val id: Int?, val name: String)
data class ToysModel(val toys: MutableList<ToyModel>)

/**
 * @author Niels Falk
 */

@location("/toys/{id}")
class toy(val id: Int)

@location("/toys")
class toys

class SwaggerKtTest {
    @Before
    fun setUp(): Unit = withTestApplication {
        //when:
        application.install(Locations)
        application.routing {
            put<toy, ToyModel>(responses(ok<ToyModel>(), notFound())) { _, _ -> }
            get<toys>(responses(ok<ToysModel>(), notFound())) { _ -> }
        }
    }

    @Test
    fun `post toy operation with path and body parameter`() {
        val parameters = swagger.find("paths", "/toys/{id}", "put").get("parameters") as List<JSONObject>
        val paraderTypes = parameters.map { it.get("in") }

        paraderTypes.should.contain("body")
        paraderTypes.should.contain("path")
    }

    @Test
    fun `post toy operation with 200 and 404 response`() {
        val responses = swagger.find("paths", "/toys/{id}", "put", "responses")

        responses.keys.should.contain("404")
        (responses.get("200") as JSONObject).find("schema").get("\$ref").should.equal("#/definitions/ToyModel")
    }

    @Test
    fun `ToysModel with array properties`() {
        val properties = swagger.find("definitions", "ToysModel", "properties", "toys")

        properties.get("type").should.equal("array")
        properties.find("items").get("\$ref").should.equal("#/definitions/ToyModel")
    }
}

private fun JSONObject.find(vararg segments: String): JSONObject {
    var current = this
    for (segment in segments) {
        current = current.get(segment) as JSONObject
    }
    return current
}
