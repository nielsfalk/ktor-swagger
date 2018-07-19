package de.nielsfalk.ktor.swagger

import de.nielsfalk.ktor.swagger.version.shared.ModelName
import de.nielsfalk.ktor.swagger.version.v3.Example
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
    internal val bodyExamples: Map<String, Example> = emptyMap(),
    internal val responses: Map<HttpStatusCode, ResponseType> = emptyMap(),
    internal val summary: String? = null,
    internal val description: String? = null,
    internal val headers: KClass<*>? = null,
    internal val parameter: KClass<*>? = null
) {
    inline fun <reified T> header(): Metadata = copy(headers = T::class)

    inline fun <reified T> parameter(): Metadata = copy(parameter = T::class)
}

fun Metadata.responds(vararg pairs: Pair<HttpStatusCode, ResponseType>): Metadata =
    copy(responses = (responses + mapOf(*pairs)))

fun Metadata.examples(vararg pairs: Pair<String, Example>): Metadata =
    copy(bodyExamples = (bodyExamples + mapOf(*pairs)))

fun Metadata.description(description: String): Metadata =
    copy(description = description)

fun Metadata.noReflectionBody(name: ModelName): Metadata =
    copy(bodySchema = BodySchema(name))

/**
 * Defines the schema reference name for the body of the message of the incoming JSON object.
 */
data class BodySchema
internal constructor(
    internal val name: ModelName?
)

fun String.description(description: String): Metadata =
    Metadata(description = description, summary = this)

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
@JvmName("noReflectionBodyReceiverIsSummary")
fun String.noReflectionBody() =
    noReflectionBody(null)

fun noReflectionBody(): Metadata =
    noReflectionBody(null)

fun String.examples(vararg pairs: Pair<String, Example>): Metadata =
    Metadata(summary = this).examples(*pairs)

fun example(
    id: String,
    value: Any? = null,
    summary: String? = null,
    description: String? = null,
    externalValue: String? = null,
    `$ref`: String? = null
): Pair<String, Example> =
    id to Example(summary, description, value, externalValue, `$ref`)

/**
 * @receiver The summary to use for the operation.
 */
fun String.responds(vararg pairs: Pair<HttpStatusCode, ResponseType>): Metadata =
    Metadata(responses = mapOf(*pairs), summary = this)

fun responds(vararg pairs: Pair<HttpStatusCode, ResponseType>) =
    Metadata(responses = mapOf(*pairs))

sealed class ResponseType() {
    abstract val examples: Map<String, Example>
}

data class ResponseFromReflection(val type: TypeInfo, override val examples: Map<String, Example>) : ResponseType()
data class ResponseSchema(val name: ModelName, override val examples: Map<String, Example>) : ResponseType()

/**
 * The type of the operation body being recived by the server.
 */
sealed class BodyType {
    abstract val examples: Map<String, Example>
}

data class BodyFromReflection(val typeInfo: TypeInfo, override val examples: Map<String, Example>) : BodyType()
data class BodyFromSchema(val name: ModelName, override val examples: Map<String, Example>) : BodyType()

inline fun <reified T> ok(vararg examples: Pair<String, Example> = arrayOf()): Pair<HttpStatusCode, ResponseType> = OK to ResponseFromReflection(
    typeInfo<T>(), mapOf(*examples)
)

fun ok(name: String, vararg examples: Pair<String, Example> = arrayOf()) = OK to ResponseSchema(
    name, mapOf(*examples)
)
inline fun <reified T> created(vararg examples: Pair<String, Example> = arrayOf()): Pair<HttpStatusCode, ResponseType> = Created to ResponseFromReflection(
    typeInfo<T>(), mapOf(*examples)
)

fun created(name: String, vararg examples: Pair<String, Example> = arrayOf()): Pair<HttpStatusCode, ResponseType> = Created to ResponseSchema(
    name, mapOf(*examples)
)

inline fun notFound(): Pair<HttpStatusCode, ResponseType> = NotFound to ResponseFromReflection(
    typeInfo<Unit>(), emptyMap()
)

@ContextDsl
inline fun <reified LOCATION : Any, reified ENTITY : Any> Route.post(
    metadata: Metadata,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(LOCATION, ENTITY) -> Unit
): Route {
    application.swaggerUi.apply {
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
    application.swaggerUi.apply {
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
    application.swaggerUi.apply {
        metadata.apply<LOCATION, Unit>(HttpMethod.Get)
    }
    return get(body)
}

@ContextDsl
inline fun <reified LOCATION : Any> Route.delete(
    metadata: Metadata,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(LOCATION) -> Unit
): Route {
    application.swaggerUi.apply {
        metadata.apply<LOCATION, Unit>(HttpMethod.Delete)
    }
    return delete(body)
}
