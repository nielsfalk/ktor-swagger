@file:Suppress("UNCHECKED_CAST")

package de.nielsfalk.ktor.swagger

import com.winterbe.expekt.should
import de.nielsfalk.ktor.swagger.version.shared.Group
import de.nielsfalk.ktor.swagger.version.shared.ParameterInputType
import de.nielsfalk.ktor.swagger.version.shared.Property
import de.nielsfalk.ktor.swagger.version.v2.Swagger
import de.nielsfalk.ktor.swagger.version.v3.OpenApi
import io.ktor.application.install
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.routing.routing
import io.ktor.server.testing.withTestApplication
import org.junit.Before
import org.junit.Test
import de.nielsfalk.ktor.swagger.version.v2.Response as ResponseV2
import de.nielsfalk.ktor.swagger.version.v3.Operation as OperationV3
import de.nielsfalk.ktor.swagger.version.v3.Response as ResponseV3

data class ToyModel(val id: Int?, val name: String) {
    companion object {
        val kiteExample = mapOf(
            "id" to 30,
            "name" to "Kite"
        )
        val trainExample = mapOf(
            "id" to 31,
            "name" to "Train"
        )
    }
}

data class ToysModel(val toys: MutableList<ToyModel>) {
    companion object {
        val example = mapOf(
            "toys" to listOf(
                ToyModel.kiteExample,
                ToyModel.trainExample
            )
        )
    }
}

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
    private lateinit var openapi: OpenApi

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
                    "update"
                        .examples(
                            example("kite", ToyModel.kiteExample),
                            example("train", ToyModel.trainExample)
                        )
                        .responds(
                            ok<ToyModel>(),
                            notFound()
                        )
                ) { _, _ -> }
                post<toys, ToyModel>(
                    "create"
                        .responds(created<ToyModel>())
                ) { _, _ -> }
                get<toys>(
                    "all".responds(
                        ok<ToysModel>(example("model", ToysModel.example)),
                        notFound()
                    )
                ) { }
                get<withParameter>("with parameter".responds(ok<Unit>()).parameter<QueryParameter>().header<Header>()) {}
            }

            this@SwaggerTest.swagger = application.swaggerUi.swagger!!
            this@SwaggerTest.openapi = application.swaggerUi.openApi!!
        }
    }

    @Test
    fun `swagger put toy operation with path and body parameter`() {
        val parameters = swagger.paths.get(toysLocation)?.get("put")?.parameters
        val paraderTypes = parameters?.map { it.`in` }

        paraderTypes.should.contain(ParameterInputType.body)
        paraderTypes.should.contain(ParameterInputType.path)
    }

    @Test
    fun `openapi put toy operation with path and body parameter`() {
        val parameters = openapi.paths.get(toysLocation)?.get("put")?.parameters
        val parameterTypes = parameters?.map { it.`in` }

        parameterTypes.should.not.contain(ParameterInputType.body)
        parameterTypes.should.contain(ParameterInputType.path)
    }

    @Test
    fun `swagger put toy operation with 200 and 404 response`() {
        val responses = swagger.paths.get(toysLocation)?.get("put")?.responses

        responses?.keys.should.contain("404")
        (responses?.get("200") as ResponseV2).schema?.`$ref`.should.equal("#/definitions/ToyModel")
    }

    @Test
    fun `openapi put toy operation with 200 and 404 response`() {
        val responses = openapi.paths.get(toysLocation)?.get("put")?.responses

        responses?.keys.should.contain("404")
        (responses?.get("200") as ResponseV3).content?.get("application/json").apply {
            should.not.be.`null`
        }!!.schema.`$ref`.should.equal("#/components/schemas/ToyModel")
    }

    @Test
    fun `openapi put toy operation with examples`() {
        val operation = (openapi.paths.get(toysLocation)?.get("put") as OperationV3)
        operation.requestBody?.content?.get("application/json")
            .apply {
                should.not.be.`null`
            }!!
            .run {
                examples["kite"]?.value.should.equal(ToyModel.kiteExample)
                examples["train"]?.value.should.equal(ToyModel.trainExample)
                Unit
            }
    }

    @Test
    fun `openapi get toys operation with examples`() {
        val operation = (openapi.paths.get(toyLocation)?.get("get") as OperationV3)
        operation.responses["200"]
            .apply {
                should.not.be.`null`
            }!!
            .run {
                this as ResponseV3
                content?.get("application/json")?.examples?.get("model")?.value.should.equal(ToysModel.example)
            }
    }

    @Test
    fun `swagger put toy operation with tag`() {
        val tags = swagger.paths.get(toysLocation)?.get("put")?.tags

        tags?.map { it.name }.should.equal(listOf("toy"))
    }

    @Test
    fun `openapi put toy operation with tag`() {
        val tags = openapi.paths.get(toysLocation)?.get("put")?.tags

        tags?.map { it.name }.should.equal(listOf("toy"))
    }

    @Test
    fun `swagger post toy operation labeled create`() {
        val responses = swagger.paths[toyLocation]?.get("post")?.responses

        responses?.keys.should.contain("201")
    }

    @Test
    fun `openapi post toy operation labeled create`() {
        val responses = openapi.paths[toyLocation]?.get("post")?.responses

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
