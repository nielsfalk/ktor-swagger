@file:Suppress("UNCHECKED_CAST")

package de.nielsfalk.playground.ktor.swagger

import com.winterbe.expekt.should
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.locations.Locations
import org.jetbrains.ktor.locations.location
import org.jetbrains.ktor.routing.routing
import org.jetbrains.ktor.testing.withTestApplication
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
            put<toy, ToyModel>("update".responds(ok<ToyModel>(), notFound())) { _, _ -> }
            get<toys>("all".responds(ok<ToysModel>(), notFound())) { _ -> }
        }
    }

    @Test
    fun `post toy operation with path and body parameter`() {
        val parameters = swagger.paths.find("/toys/{id}", "put")["parameters"] as List<MutableMap<String, Any>>
        val paraderTypes = parameters.map { it["in"] }

        paraderTypes.should.contain("body")
        paraderTypes.should.contain("path")
    }

    @Test
    fun `post toy operation with 200 and 404 response`() {
        val responses = swagger.paths.find("/toys/{id}", "put", "responses")

        responses.keys.should.contain("404")
        (responses["200"] as MutableMap<String, Any>).find("schema")["\$ref"].should.equal("#/definitions/ToyModel")
    }

    @Test
    fun `ToysModel with array properties`() {
        val properties = swagger.definitions.find("ToysModel", "properties", "toys")

        properties["type"].should.equal("array")
        properties.find("items")["\$ref"].should.equal("#/definitions/ToyModel")
    }
}

private fun MutableMap<String, Any>.find(vararg segments: String): MutableMap<String, Any> {
    var current = this
    for (segment in segments) {
        current = current[segment] as MutableMap<String, Any>
    }
    return current
}
