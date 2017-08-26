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

class SwaggerTest {
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
        val parameters = swagger.paths.get("/toys/{id}")?.get("put")?.parameters
        val paraderTypes = parameters?.map { it.`in` }

        paraderTypes.should.contain(ParameterInputType.body)
        paraderTypes.should.contain(ParameterInputType.path)
    }

    @Test
    fun `post toy operation with 200 and 404 response`() {
        val responses = swagger.paths.get("/toys/{id}")?.get("put")?.responses

        responses?.keys.should.contain("404")
        responses?.get("200")?.schema?.`$ref`.should.equal("#/definitions/ToyModel")
    }

    @Test
    fun `ToysModel with array properties`() {
        val toys = swagger.definitions.get("ToysModel")?.properties?.get("toys") as ArrayModelModelProperty

        toys?.type.should.equal("array")
        toys?.items.`$ref`.should.equal("#/definitions/ToyModel")
    }

    enum class EnumClass {
        first, second, third
    }

    class Model(
            val enumValue: EnumClass
    )

    @Test
    fun `enum Property`() {
        val property = ModelData(Model::class)
                .properties["enumValue"] as EnumModelProperty

        property.type.should.equal("string")
        property.enum.should.contain.elements("first", "second", "third")
    }
}

private fun MutableMap<String, Any>.find(vararg segments: String): MutableMap<String, Any> {
    var current = this
    for (segment in segments) {
        current = current[segment] as MutableMap<String, Any>
    }
    return current
}
