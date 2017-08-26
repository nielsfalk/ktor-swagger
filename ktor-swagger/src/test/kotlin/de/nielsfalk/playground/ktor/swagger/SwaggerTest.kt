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
import java.time.Instant
import java.time.LocalDate

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
        val toys = swagger.definitions.get("ToysModel")?.properties?.get("toys") as ArrayModelProperty

        toys?.type.should.equal("array")
        val items = toys?.items as ReferenceModelProperty
        items.`$ref`.should.equal("#/definitions/ToyModel")
    }

    enum class EnumClass {
        first, second, third
    }

    @Test
    fun `enum Property`() {
        class Model(val enumValue: EnumClass?)

        val property = ModelData(Model::class)
                .properties["enumValue"] as EnumModelProperty

        property.type.should.equal("string")
        property.enum.should.contain.elements("first", "second", "third")
    }

    @Test
    fun `instant Property`() {
        class Model(val timestamp: Instant?)

        val property = ModelData(Model::class)
                .properties["timestamp"] as ModelProperty

        property.type.should.equal("string")
        property.format.should.equal("date-time")
    }

    @Test
    fun `localDate Property`() {
        class Model(val birthDate: LocalDate?)

        val property = ModelData(Model::class)
                .properties["birthDate"] as ModelProperty

        property.type.should.equal("string")
        property.format.should.equal("date")
    }

    @Test
    fun `long Property`() {
        class Model(val long: Long?)

        val property = ModelData(Model::class)
                .properties["long"] as ModelProperty

        property.type.should.equal("integer")
        property.format.should.equal("int64")
    }

    @Test
    fun `double Property`() {
        class Model(val double: Double?)

        val property = ModelData(Model::class)
                .properties["double"] as ModelProperty

        property.type.should.equal("number")
        property.format.should.equal("double")
    }

    class PropertyModel {}

    @Test
    fun `reference model property`() {
        class Model(val something: PropertyModel?)

        val property = ModelData(Model::class)
                .properties["something"] as ReferenceModelProperty

        property.`$ref`.should.equal("#/definitions/PropertyModel")
    }

    @Test
    fun `string array`() {
        class Model(val something: List<String>)

        val property = ModelData(Model::class)
                .properties["something"] as ArrayModelProperty

        property.type.should.equal("array")
        property.items.type.should.equal("string")
    }
}

private fun MutableMap<String, Any>.find(vararg segments: String): MutableMap<String, Any> {
    var current = this
    for (segment in segments) {
        current = current[segment] as MutableMap<String, Any>
    }
    return current
}
