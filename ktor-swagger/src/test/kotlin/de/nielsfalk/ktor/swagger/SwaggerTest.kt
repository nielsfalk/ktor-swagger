@file:Suppress("UNCHECKED_CAST")

package de.nielsfalk.ktor.swagger

import com.winterbe.expekt.should
import de.nielsfalk.ktor.swagger.version.shared.Group
import de.nielsfalk.ktor.swagger.version.shared.ParameterInputType
import de.nielsfalk.ktor.swagger.version.shared.Property
import de.nielsfalk.ktor.swagger.version.v2.Response
import de.nielsfalk.ktor.swagger.version.v2.Swagger
import de.nielsfalk.ktor.swagger.version.v3.OpenApi
import io.ktor.application.install
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.routing.routing
import io.ktor.server.testing.withTestApplication
import org.junit.Before
import org.junit.Test

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
            install(SwaggerSupport) {
                swagger = Swagger()
                openApi = OpenApi()
            }
        }) {
            // when:
            application.routing {
                put<toy, ToyModel>(
                    "update".responds(
                        ok<ToyModel>(),
                        notFound()
                    )
                ) { _, _ -> }
                post<toys, ToyModel>("create".responds(created<ToyModel>())) { _, _ -> }
                get<toys>(
                    "all".responds(
                        ok<ToysModel>(),
                        notFound()
                    )
                ) { }
                get<withParameter>("with parameter".responds(ok<Unit>()).parameter<QueryParameter>().header<Header>()) {}
            }

            this@SwaggerTest.swagger = application.swaggerUi.swagger!!
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
        (responses?.get("200") as Response).schema?.`$ref`.should.equal("#/definitions/ToyModel")
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

        toys.type.should.equal("array")
        val items = toys.items as Property
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
}
