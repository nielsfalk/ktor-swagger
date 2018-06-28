package de.nielsfalk.playground.ktor.swagger

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.Compression
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.pipeline.PipelineContext
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.StringValues
import io.ktor.util.toMap
import java.lang.Integer.getInteger

data class PetModel(val id: Int?, val name: String)

data class PetsModel(val pets: MutableList<PetModel>)

val sizeSchemaMap = mapOf(
    "type" to "number",
    "minimum" to 0
)

val rectangleSchemaMap = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "a" to mapOf("${'$'}ref" to "#/definitions/size"),
        "b" to mapOf("${'$'}ref" to "#/definitions/size")
    )
)

val data = PetsModel(mutableListOf(PetModel(1, "max"), PetModel(2, "moritz")))
fun newId() = ((data.pets.map { it.id ?: 0 }.max()) ?: 0) + 1

@Group("pet operations")
@Location("/pets/{id}")
class pet(val id: Int)

@Group("pet operations")
@Location("/pets")
class pets

@Group("shape operations")
@Location("/shapes")
class shapes

@Group("debug")
@Location("/request/info")
class requestInfo

@Group("debug")
@Location("/request/withHeader")
class withHeader

class Header(val optionalHeader: String?, val mandatoryHeader: Int)

@Group("debug")
@Location("/request/withQueryParameter")
class withQueryParameter

class QueryParameter(val optionalParameter: String?, val mandatoryParameter: Int)

fun main(args: Array<String>) {
    val server = embeddedServer(Netty, getInteger("server.port", 8080)) {
        install(DefaultHeaders)
        install(Compression)
        install(CallLogging)
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
            }
        }
        install(Locations)
        install(SwaggerSupport) {
            forwardRoot = true
            swagger.info = Information(
                version = "0.1",
                title = "sample api implemented in ktor",
                description = "This is a sample which combines [ktor](https://github.com/Kotlin/ktor) with [swaggerUi](https://swagger.io/). You find the sources on [github](https://github.com/nielsfalk/ktor-swagger)",
                contact = Contact(
                    name = "Niels Falk",
                    url = "https://nielsfalk.de"
                )
            )
            swagger.definitions["size"] = sizeSchemaMap
        }
        routing {
            get<pets>("all".responds(ok<PetsModel>())) {
                call.respond(data)
            }
            post<pets, PetModel>("create".responds(created<PetModel>())) { _, entity ->
                call.respond(Created, entity.copy(id = newId()).apply {
                    data.pets.add(this)
                })
            }
            get<pet>("find".responds(ok<PetModel>(), notFound())) { params ->
                data.pets.find { it.id == params.id }
                    ?.let {
                        call.respond(it)
                    }
            }
            put<pet, PetModel>("update".responds(ok<PetModel>(), notFound())) { params, entity ->
                if (data.pets.removeIf { it.id == params.id && it.id == entity.id }) {
                    data.pets.add(entity)
                    call.respond(entity)
                }
            }
            delete<pet>("delete".responds(ok<Unit>(), notFound())) { params ->
                if (data.pets.removeIf { it.id == params.id }) {
                    call.respond(Unit)
                }
            }
            get<shapes>("all".responds(ok("Rectangle", rectangleSchemaMap))) {
                call.respondText("""
                    {
                        "a" : 10,
                        "b" : 25
                    }
                """.trimIndent(), ContentType.Application.Json)
            }
            get<requestInfo>(
                responds(ok<Unit>()),
                respondRequestDetails()
            )
            get<withQueryParameter>(
                responds(ok<Unit>())
                    .parameter<QueryParameter>(),
                respondRequestDetails()
            )
            get<withHeader>(
                responds(ok<Unit>())
                    .header<Header>(),
                respondRequestDetails()
            )
        }
    }
    server.start(wait = true)
}

fun respondRequestDetails(): suspend PipelineContext<Unit, ApplicationCall>.(Any) -> Unit {
    return {
        call.respond(
            mapOf(
                "parameter" to call.parameters,
                "header" to call.request.headers
            ).format()
        )
    }
}

private fun Map<String, StringValues>.format() =
    mapValues {
        it.value.toMap()
            .flatMap { (key, value) -> value.map { key to it } }
            .map { (key, value) -> "$key: $value" }
            .joinToString(separator = ",\n")
    }
        .map { (key, value) -> "$key:\n$value" }
        .joinToString(separator = "\n\n")
