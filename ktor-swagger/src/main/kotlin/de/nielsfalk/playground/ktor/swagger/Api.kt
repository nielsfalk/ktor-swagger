package de.nielsfalk.playground.ktor.swagger

import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.http.HttpStatusCode.Companion.NotFound
import org.jetbrains.ktor.http.HttpStatusCode.Companion.OK
import org.jetbrains.ktor.locations.handle
import org.jetbrains.ktor.locations.location
import org.jetbrains.ktor.locations.post
import org.jetbrains.ktor.locations.put
import org.jetbrains.ktor.pipeline.PipelineContext
import org.jetbrains.ktor.request.receive
import org.jetbrains.ktor.routing.Route
import org.jetbrains.ktor.routing.method
import kotlin.reflect.KClass

/**
 * @author Niels Falk
 */

data class Metadata(val responses: Map<HttpStatusCode, KClass<*>>, val summary:String?=null)

fun String.responds(vararg pairs: Pair<HttpStatusCode, KClass<*>>): Metadata = Metadata(responses = mapOf(*pairs), summary = this)

fun responds(pair: Pair<HttpStatusCode, KClass<*>>) = Metadata(responses = mapOf(pair))
fun responses(vararg pairs: Pair<HttpStatusCode, KClass<*>>) = Metadata(responses = mapOf(*pairs))

inline fun <reified T> ok(): Pair<HttpStatusCode, KClass<*>> = OK to T::class
inline fun notFound(): Pair<HttpStatusCode, KClass<*>> = NotFound to Unit::class

inline fun <reified LOCATION : Any, reified ENTITY : Any> Route.post(metadata: Metadata, noinline body: suspend PipelineContext<Unit>.(LOCATION, ENTITY) -> Unit): Route {
    metadata.apply<LOCATION, ENTITY>(HttpMethod.Post)
    return post<LOCATION> {
        body(this, it, call.receive())
    }
}

inline fun <reified LOCATION : Any, reified ENTITY : Any> Route.put(metadata: Metadata, noinline body: suspend PipelineContext<Unit>.(LOCATION, ENTITY) -> Unit): Route {
    metadata.apply<LOCATION, ENTITY>(HttpMethod.Put)
    return put<LOCATION> {
        body(this, it, call.receive())
    }
}

inline fun <reified LOCATION : Any> Route.get(metadata: Metadata, noinline body: suspend PipelineContext<Unit>.(LOCATION) -> Unit): Route {
    metadata.apply<LOCATION, Unit>(HttpMethod.Get)
    return location(LOCATION::class) {
        method(HttpMethod.Get) {
            handle(body)
        }
    }
}

inline fun <reified LOCATION : Any> Route.delete(metadata: Metadata, noinline body: suspend PipelineContext<Unit>.(LOCATION) -> Unit): Route {
    metadata.apply<LOCATION, Unit>(HttpMethod.Delete)
    return location(LOCATION::class) {
        method(HttpMethod.Delete) {
            handle(body)
        }
    }
}
