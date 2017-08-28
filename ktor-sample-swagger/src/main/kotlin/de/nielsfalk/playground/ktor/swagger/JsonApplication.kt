package de.nielsfalk.playground.ktor.swagger

import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.features.CallLogging
import org.jetbrains.ktor.features.Compression
import org.jetbrains.ktor.features.DefaultHeaders
import org.jetbrains.ktor.gson.GsonSupport
import org.jetbrains.ktor.host.embeddedServer
import org.jetbrains.ktor.locations.Locations
import org.jetbrains.ktor.locations.location
import org.jetbrains.ktor.netty.Netty
import org.jetbrains.ktor.pipeline.PipelineContext
import org.jetbrains.ktor.response.respond
import org.jetbrains.ktor.routing.routing
import org.jetbrains.ktor.util.ValuesMap
import org.jetbrains.ktor.util.toMap
import java.lang.Integer.getInteger

/**
 * @author Niels Falk
 */

data class PetModel(val id: Int?, val name: String)

data class PetsModel(val pets: MutableList<PetModel>)

val data = PetsModel(mutableListOf(PetModel(1, "max"), PetModel(2, "moritz")))
fun newId() = ((data.pets.map { it.id ?: 0 }.max()) ?: 0) + 1

@group("pet operations")
@location("/pets/{id}")
class pet(val id: Int)

@group("pet operations")
@location("/pets")
class pets

@group("debug")
@location("/request/info")
class requestInfo

@group("debug")
@location("/request/withHeader")
class withHeader

class Header(val optionalHeader: String?, val mandatoryHeader: Int)

@group("debug")
@location("/request/withQueryParameter")
class withQueryParameter

class QueryParameter(val optionalParameter: String?, val mandatoryParameter: Int)

fun main(args: Array<String>) {
    val server = embeddedServer(Netty, getInteger("server.port", 8080)) {
        install(DefaultHeaders)
        install(Compression)
        install(CallLogging)
        install(GsonSupport) {
            setPrettyPrinting()
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
                            url = "https://nielsfalk.de")
            )
        }
        routing {
            get<pets>("all".responds(ok<PetsModel>())) {
                call.respond(data)
            }
            post<pets, PetModel>("create".responds(ok<PetModel>())) { _, entity ->
                //http201 would be better but there is no way to do this see org.jetbrains.ktor.gson.GsonSupport.renderJsonContent
                call.respond(entity.copy(id = newId()).apply {
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
            get<requestInfo>(responds(ok<Unit>()),
                    respondRequestDetails())
            get<withQueryParameter>(responds(ok<Unit>())
                    .parameter<QueryParameter>(),
                    respondRequestDetails())
            get<withHeader>(responds(ok<Unit>())
                    .header<Header>(),
                    respondRequestDetails())
        }
    }
    server.start(wait = true)
}

fun respondRequestDetails(): suspend PipelineContext<Unit>.(Any) -> Unit {
    return {
        call.respond(mapOf(
                "parameter" to call.parameters,
                "header" to call.request.headers
        ).format())
    }
}

private fun Map<String, ValuesMap>.format() =
        mapValues {
            it.value.toMap()
                    .flatMap { (key, value) -> value.map { key to it } }
                    .map { (key, value) -> "$key: $value" }
                    .joinToString(separator = ",\n")
        }
                .map { (key, value) -> "$key:\n$value" }
                .joinToString(separator = "\n\n")

