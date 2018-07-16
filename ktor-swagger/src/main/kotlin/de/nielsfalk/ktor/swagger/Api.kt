package de.nielsfalk.ktor.swagger

import de.nielsfalk.ktor.swagger.version.shared.ModelName
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.client.call.TypeInfo
import io.ktor.client.call.typeInfo
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
    internal val bodySchema: BodySchema? = null,
    internal val responses: Map<HttpStatusCode, ResponseType> = emptyMap(),
    internal val summary: String? = null,
    internal val headers: KClass<*>? = null,
    internal val parameter: KClass<*>? = null
) {
    inline fun <reified T> header(): Metadata = copy(headers = T::class)

    inline fun <reified T> parameter(): Metadata = copy(parameter = T::class)
}

fun Metadata.responds(vararg pairs: Pair<HttpStatusCode, ResponseType>): Metadata =
    copy(responses = (responses + mapOf(*pairs)))

/**
 * Defines the schema reference name for the body of the message of the incoming JSON object.
 */
data class BodySchema
internal constructor(
    internal val name: ModelName?
)

/**
 * Define a custom schema for the body of the HTTP request.
 * @param name The model name to use in the Swagger Schema.
 * @receiver The summary to use for the operation.
 */
fun String.noReflectionBody(name: ModelName?): Metadata =
    Metadata(bodySchema = BodySchema(name), summary = this)

fun noReflectionBody(name: ModelName?): Metadata =
    Metadata(bodySchema = BodySchema(name))

/**
 * Define a custom schema for the body of the HTTP request.
 * The name will be infered from the reified `ENTITY` type.
 * @receiver The summary to use for the operation.
 */
@JvmName("noReflectionBodyReciverIsSummary")
fun String.noReflectionBody() =
    noReflectionBody(null)

fun noReflectionBody(): Metadata =
    noReflectionBody(null)

/**
 * @receiver The summary to use for the operation.
 */
fun String.responds(vararg pairs: Pair<HttpStatusCode, ResponseType>): Metadata =
    Metadata(responses = mapOf(*pairs), summary = this)

fun responds(vararg pairs: Pair<HttpStatusCode, ResponseType>) =
    Metadata(responses = mapOf(*pairs))

sealed class ResponseType

data class ResponseFromReflection(val type: TypeInfo) : ResponseType()
data class ResponseSchema(val name: ModelName) : ResponseType()

/**
 * The type of the operation body being recived by the server.
 */
sealed class BodyType

data class BodyFromReflection(val typeInfo: TypeInfo) : BodyType()
data class BodyFromSchema(val name: ModelName) : BodyType()

inline fun <reified T> ok(): Pair<HttpStatusCode, ResponseType> = OK to ResponseFromReflection(
    typeInfo<T>()
)

fun ok(name: String) = OK to ResponseSchema(name)
inline fun <reified T> created(): Pair<HttpStatusCode, ResponseType> = Created to ResponseFromReflection(
    typeInfo<T>()
)

fun created(name: String): Pair<HttpStatusCode, ResponseType> = Created to ResponseSchema(
    name
)

inline fun notFound(): Pair<HttpStatusCode, ResponseType> = NotFound to ResponseFromReflection(
    typeInfo<Unit>()
)

@ContextDsl
inline fun <reified LOCATION : Any, reified ENTITY : Any> Route.post(
    metadata: Metadata,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(LOCATION, ENTITY) -> Unit
): Route {
    application.swagger.apply {
        metadata.apply<LOCATION, ENTITY>(HttpMethod.Post)
    }

    return post<LOCATION> {
        body(this, it, call.receive())
    }
}

@ContextDsl
inline fun <reified LOCATION : Any, reified ENTITY : Any> Route.put(
    metadata: Metadata,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(LOCATION, ENTITY) -> Unit
): Route {
    application.swagger.apply {
        metadata.apply<LOCATION, ENTITY>(HttpMethod.Put)
    }
    return put<LOCATION> {
        body(this, it, call.receive())
    }
}

@ContextDsl
inline fun <reified LOCATION : Any> Route.get(
    metadata: Metadata,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(LOCATION) -> Unit
): Route {
    application.swagger.apply {
        metadata.apply<LOCATION, Unit>(HttpMethod.Get)
    }
    return get(body)
}

@ContextDsl
inline fun <reified LOCATION : Any> Route.delete(
    metadata: Metadata,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(LOCATION) -> Unit
): Route {
    application.swagger.apply {
        metadata.apply<LOCATION, Unit>(HttpMethod.Delete)
    }
    return delete(body)
}
