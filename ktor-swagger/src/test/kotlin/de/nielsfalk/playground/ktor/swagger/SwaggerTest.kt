@file:Suppress("UNCHECKED_CAST")

package de.nielsfalk.playground.ktor.swagger

import com.winterbe.expekt.should
import io.ktor.application.install
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.routing.routing
import io.ktor.server.testing.withTestApplication
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

data class ToyModel(val id: Int?, val name: String)
data class ToysModel(val toys: MutableList<ToyModel>)

const val toysLocation = "/toys/{id}"
@Group("toy")
@Location(toysLocation)
class toy(val id: Int)

const val toyLocation = "/toys"
@Location(toyLocation)
class toys

@Location("/withParameter")
class withParameter

class Header(val optionalHeader: String?, val mandatoryHeader: Int)
class QueryParameter(val optionalParameter: String?, val mandatoryParameter: Int)

class SwaggerTest {
    private lateinit var swagger: Swagger

    @Before
    fun setUp() {
        withTestApplication({
            install(Locations)
            install(SwaggerSupport)
        }) {
            // when:
            application.routing {
                put<toy, ToyModel>("update".responds(ok<ToyModel>(), notFound())) { _, _ -> }
                post<toys, ToyModel>("create".responds(created<ToyModel>())) { _, _ -> }
                get<toys>("all".responds(ok<ToysModel>(), notFound())) { }
                get<withParameter>("with parameter".responds(ok<Unit>()).parameter<QueryParameter>().header<Header>()) {}
            }

            this@SwaggerTest.swagger = application.swagger.swagger
        }
    }

    @Test
    fun `put toy operation with path and body parameter`() {
        val parameters = swagger.paths.get(toysLocation)?.get("put")?.parameters
        val paraderTypes = parameters?.map { it.`in` }

        paraderTypes.should.contain(ParameterInputType.body)
        paraderTypes.should.contain(ParameterInputType.path)
    }

    @Test
    fun `put toy operation with 200 and 404 response`() {
        val responses = swagger.paths.get(toysLocation)?.get("put")?.responses

        responses?.keys.should.contain("404")
        responses?.get("200")?.schema?.`$ref`.should.equal("#/definitions/ToyModel")
    }

    @Test
    fun `put toy operation with tag`() {
        val tags = swagger.paths.get(toysLocation)?.get("put")?.tags

        tags?.map { it.name }.should.equal(listOf("toy"))
    }

    @Test
    fun `post toy operation labeled create`() {
        val responses = swagger.paths[toyLocation]?.get("post")?.responses

        responses?.keys.should.contain("201")
    }

    @Test
    fun `ToysModel with array properties`() {
        val toys = (swagger.definitions.get("ToysModel") as? ModelData)?.properties?.get("toys") as Property

        toys?.type.should.equal("array")
        val items = toys?.items as Property
        items.`$ref`.should.equal("#/definitions/ToyModel")
    }

    @Test
    fun `query parameter`() {
        val parameters = swagger.paths.get("/withParameter")?.get("get")?.parameters

        val optional = parameters?.find { it.name == "optionalParameter" }
        optional?.required.should.equal(false)
        optional?.`in`.should.equal(ParameterInputType.query)

        val mandatory = parameters?.find { it.name == "mandatoryParameter" }
        mandatory?.required.should.equal(true)
        mandatory?.`in`.should.equal(ParameterInputType.query)
    }

    @Test
    fun `headers`() {
        val parameters = swagger.paths.get("/withParameter")?.get("get")?.parameters

        val optional = parameters?.find { it.name == "optionalHeader" }
        optional?.required.should.equal(false)
        optional?.`in`.should.equal(ParameterInputType.header)

        val mandatory = parameters?.find { it.name == "mandatoryHeader" }
        mandatory?.required.should.equal(true)
        mandatory?.`in`.should.equal(ParameterInputType.header)
    }

    enum class EnumClass {
        first, second, third
    }

    private fun createAndExtractModelData(kClass: KClass<*>) =
        createModelData(kClass).first

    @Test
    fun `enum Property`() {
        class Model(val enumValue: EnumClass?)

        val property = createAndExtractModelData(Model::class)
            .properties["enumValue"] as Property

        property.type.should.equal("string")
        property.enum.should.contain.elements("first", "second", "third")
    }

    @Test
    fun `instant Property`() {
        class Model(val timestamp: Instant?)

        val property = createAndExtractModelData(Model::class)
            .properties["timestamp"] as Property

        property.type.should.equal("string")
        property.format.should.equal("date-time")
    }

    @Test
    fun `localDate Property`() {
        class Model(val birthDate: LocalDate?)

        val property = createAndExtractModelData(Model::class)
            .properties["birthDate"] as Property

        property.type.should.equal("string")
        property.format.should.equal("date")
    }

    @Test
    fun `long Property`() {
        class Model(val long: Long?)

        val property = createAndExtractModelData(Model::class)
            .properties["long"] as Property

        property.type.should.equal("integer")
        property.format.should.equal("int64")
    }

    @Test
    fun `double Property`() {
        class Model(val double: Double?)

        val property = createAndExtractModelData(Model::class)
            .properties["double"] as Property

        property.type.should.equal("number")
        property.format.should.equal("double")
    }

    class PropertyModel

    @Test
    fun `reference model property`() {
        class Model(val something: PropertyModel?)

        val property = createAndExtractModelData(Model::class)
            .properties["something"] as Property

        property.`$ref`.should.equal("#/definitions/PropertyModel")
    }

    @Test
    fun `string array`() {
        class Model(val something: List<String>)

        val property = createAndExtractModelData(Model::class)
            .properties["something"] as Property

        property.type.should.equal("array")
        property.items?.type.should.equal("string")
    }

    class Parameters(val optional: String?, val mandatory: String)

    @Test
    fun `optional parameters`() {
        val map = Parameters::class.memberProperties.map { it.toParameter("").first }

        map.find { it.name == "optional" }!!.required.should.equal(false)
        map.find { it.name == "mandatory" }!!.required.should.equal(true)
    }
}

private fun MutableMap<String, Any>.find(vararg segments: String): MutableMap<String, Any> {
    var current = this
    for (segment in segments) {
        current = current[segment] as MutableMap<String, Any>
    }
    return current
}
