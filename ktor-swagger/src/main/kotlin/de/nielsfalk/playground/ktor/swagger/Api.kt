package de.nielsfalk.playground.ktor.swagger

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.delete
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.locations.put
import io.ktor.pipeline.ContextDsl
import io.ktor.pipeline.PipelineContext
import io.ktor.request.receive
import io.ktor.routing.Route
import io.ktor.routing.application
import kotlin.reflect.KClass

data class Metadata(
    val responses: Map<HttpStatusCode, ResponseType>,
    val summary: String? = null,
    val headers: KClass<*>? = null,
    val parameter: KClass<*>? = null
) {
    inline fun <reified T> header(): Metadata = copy(headers = T::class)

    inline fun <reified T> parameter(): Metadata = copy(parameter = T::class)
}

fun String.responds(vararg pairs: Pair<HttpStatusCode, ResponseType>): Metadata = Metadata(responses = mapOf(*pairs), summary = this)

fun responds(pair: Pair<HttpStatusCode, ResponseType>) = Metadata(responses = mapOf(pair))
fun responses(vararg pairs: Pair<HttpStatusCode, ResponseType>) = Metadata(responses = mapOf(*pairs))

sealed class ResponseType

data class ResponseFromReflection(val kClass: KClass<*>) : ResponseType()
data class ResponseSchema(val name: String, val schema: Any) : ResponseType()

inline fun <reified T> ok(): Pair<HttpStatusCode, ResponseType> = OK to ResponseFromReflection(T::class)
fun ok(name: String, schema: Any) = OK to ResponseSchema(name, schema)
inline fun <reified T> created(): Pair<HttpStatusCode, ResponseType> = Created to ResponseFromReflection(T::class)
inline fun notFound(): Pair<HttpStatusCode, ResponseType> = NotFound to ResponseFromReflection(Unit::class)

@ContextDsl
inline fun <reified LOCATION : Any, reified ENTITY : Any> Route.post(metadata: Metadata, noinline body: suspend PipelineContext<Unit, ApplicationCall>.(LOCATION, ENTITY) -> Unit): Route {
    application.swagger.apply {
        metadata.apply<LOCATION, ENTITY>(HttpMethod.Post)
    }

    return post<LOCATION> {
        body(this, it, call.receive())
    }
}

@ContextDsl
inline fun <reified LOCATION : Any, reified ENTITY : Any> Route.put(metadata: Metadata, noinline body: suspend PipelineContext<Unit, ApplicationCall>.(LOCATION, ENTITY) -> Unit): Route {
    application.swagger.apply {
        metadata.apply<LOCATION, ENTITY>(HttpMethod.Put)
    }
    return put<LOCATION> {
        body(this, it, call.receive())
    }
}

@ContextDsl
inline fun <reified LOCATION : Any> Route.get(metadata: Metadata, noinline body: suspend PipelineContext<Unit, ApplicationCall>.(LOCATION) -> Unit): Route {
    application.swagger.apply {
        metadata.apply<LOCATION, Unit>(HttpMethod.Get)
    }
    return get(body)
}

@ContextDsl
inline fun <reified LOCATION : Any> Route.delete(metadata: Metadata, noinline body: suspend PipelineContext<Unit, ApplicationCall>.(LOCATION) -> Unit): Route {
    application.swagger.apply {
        metadata.apply<LOCATION, Unit>(HttpMethod.Delete)
    }
    return delete(body)
}
