package de.nielsfalk.ktor.swagger.sample

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import de.nielsfalk.ktor.swagger.SwaggerSupport
import de.nielsfalk.ktor.swagger.created
import de.nielsfalk.ktor.swagger.delete
import de.nielsfalk.ktor.swagger.description
import de.nielsfalk.ktor.swagger.example
import de.nielsfalk.ktor.swagger.examples
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.patch
import de.nielsfalk.ktor.swagger.post
import de.nielsfalk.ktor.swagger.put
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Contact
import de.nielsfalk.ktor.swagger.version.shared.Group
import de.nielsfalk.ktor.swagger.version.shared.Information
import de.nielsfalk.ktor.swagger.version.v2.Swagger
import de.nielsfalk.ktor.swagger.version.v3.OpenApi
import de.nielsfalk.ktor.swagger.version.v3.Schema
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
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.StringValues
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.toMap

data class PetModel(val id: Int?, val name: String) {
    companion object {
        val exampleSpike = mapOf(
            "id" to 1,
            "name" to "Spike"
        )

        val exampleRover = mapOf(
            "id" to 2,
            "name" to "Rover"
        )
    }
}

data class PetsModel(val pets: MutableList<PetModel>) {
    companion object {
        val exampleModel = mapOf(
            "pets" to listOf(
                PetModel.exampleSpike,
                PetModel.exampleRover
            )
        )
    }
}

data class Model<T>(val elements: MutableList<T>)

val sizeSchemaMap = mapOf(
    "type" to "number",
    "minimum" to 0
)

fun rectangleSchemaMap(refBase: String) = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "a" to mapOf("${'$'}ref" to "$refBase/size"),
        "b" to mapOf("${'$'}ref" to "$refBase/size")
    )
)

val data = PetsModel(
    mutableListOf(
        PetModel(1, "max"),
        PetModel(2, "moritz")
    )
)

fun newId() = ((data.pets.map { it.id ?: 0 }.max()) ?: 0) + 1

@Group("pet operations")
@Location("/pets/{id}")
class pet(val id: Int)

@Group("pet operations")
@Location("/pets")
class pets

@Group("generic operations")
@Location("/genericPets")
class genericPets

const val petUuid = "petUuid"

@Group("generic operations")
@Location("/pet/custom/{id}")
class petCustomSchemaParam(
    @Schema(petUuid)
    val id: String
)

val petIdSchema = mapOf(
    "type" to "string",
    "format" to "date",
    "description" to "The identifier of the pet to be accessed"
)

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

internal fun run(port: Int, wait: Boolean = true): ApplicationEngine {
    println("Launching on port `$port`")
    val server = embeddedServer(Netty, port) {
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
            val information = Information(
                version = "0.1",
                title = "sample api implemented in ktor",
                description = "This is a sample which combines [ktor](https://github.com/Kotlin/ktor) with [swaggerUi](https://swagger.io/). You find the sources on [github](https://github.com/nielsfalk/ktor-swagger)",
                contact = Contact(
                    name = "Niels Falk",
                    url = "https://nielsfalk.de"
                )
            )
            swagger = Swagger().apply {
                info = information
                definitions["size"] = sizeSchemaMap
                definitions[petUuid] = petIdSchema
                definitions["Rectangle"] = rectangleSchemaMap("#/definitions")
            }
            openApi = OpenApi().apply {
                info = information
                components.schemas["size"] = sizeSchemaMap
                components.schemas[petUuid] = petIdSchema
                components.schemas["Rectangle"] = rectangleSchemaMap("#/components/schemas")
            }
        }
        routing {
            get<pets>("all".responds(ok<PetsModel>(example("model", PetsModel.exampleModel)))) {
                call.respond(data)
            }
            post<pets, PetModel>(
                "create"
                    .description("Save a pet in our wonderful database!")
                    .examples(
                        example("rover", PetModel.exampleRover, summary = "Rover is one possible pet."),
                        example("spike", PetModel.exampleSpike, summary = "Spike is a different posssible pet.")
                    )
                    .responds(
                        created<PetModel>(
                            example("rover", PetModel.exampleRover),
                            example("spike", PetModel.exampleSpike)
                        )
                    )
            ) { _, entity ->
                call.respond(Created, entity.copy(id = newId()).apply {
                    data.pets.add(this)
                })
            }
            get<pet>(
                "find".responds(
                    ok<PetModel>(),
                    notFound()
                )
            ) { params ->
                data.pets.find { it.id == params.id }
                    ?.let {
                        call.respond(it)
                    }
            }
            put<pet, PetModel>(
                "update".responds(
                    ok<PetModel>(),
                    notFound()
                )
            ) { params, entity ->
                if (data.pets.removeIf { it.id == params.id && it.id == entity.id }) {
                    data.pets.add(entity)
                    call.respond(entity)
                }
            }

            patch<pet, PetModel>(
                    "update with patch".responds(
                            ok<PetModel>(),
                            notFound()
                    )
            ) { params, entity ->
                if (data.pets.removeIf { it.id == params.id && it.id == entity.id }) {
                    data.pets.add(entity)
                    call.respond(entity)
                }
            }
            delete<pet>(
                "delete".responds(
                    ok<Unit>(),
                    notFound()
                )
            ) { params ->
                if (data.pets.removeIf { it.id == params.id }) {
                    call.respond(Unit)
                }
            }
            get<shapes>(
                "all".responds(
                    ok("Rectangle")
                )
            ) {
                call.respondText(
                    """
                    {
                        "a" : 10,
                        "b" : 25
                    }
                """.trimIndent(), ContentType.Application.Json
                )
            }
            get<genericPets>("all".responds(ok<Model<PetModel>>())) {
                call.respond(data)
            }

            get<petCustomSchemaParam>("pet by id".responds(ok<PetModel>())) {
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
    return server.start(wait = wait)
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

/**
 * Launches the application and handles the args passed to [main].
 */
class Launcher : CliktCommand(
    name = "ktor-sample-swagger"
) {
    companion object {
        private const val defaultPort = 8080
    }

    private val port: Int by option(
        "-p",
        "--port",
        help = "The port that this server should be started on. Defaults to $defaultPort."
    )
        .int()
        .default(defaultPort)

    override fun run() {
        run(port)
    }
}

fun main(args: Array<String>) = Launcher().main(args)
