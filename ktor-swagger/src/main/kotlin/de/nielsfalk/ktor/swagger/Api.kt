package de.nielsfalk.ktor.swagger

import de.nielsfalk.ktor.swagger.version.shared.ModelName
import de.nielsfalk.ktor.swagger.version.v3.Example
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.client.call.TypeInfo
import io.ktor.client.call.typeInfo
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.delete
import io.ktor.locations.get
import io.ktor.locations.patch
import io.ktor.locations.post
import io.ktor.locations.put
import io.ktor.request.receive
import io.ktor.routing.Route
import io.ktor.routing.application
import io.ktor.util.pipeline.ContextDsl
import io.ktor.util.pipeline.PipelineContext
import kotlin.reflect.KClass

data class Metadata(
    internal val bodySchema: BodySchema? = null,
    internal val bodyExamples: Map<String, Example> = emptyMap(),
    internal val responses: List<HttpCodeResponse> = emptyList(),
    internal val summary: String? = null,
    internal val description: String? = null,
    internal val requirements: List<Map<String, List<String>>>? = null,
    @PublishedApi
    internal val headers: List<KClass<*>> = emptyList(),
    @PublishedApi
    internal val parameters: List<KClass<*>> = emptyList(),
    internal val operationId: String? = null
) {
    inline fun <reified T> header(): Metadata = copy(headers = headers + T::class)

    inline fun <reified T> parameter(): Metadata = copy(parameters = parameters + T::class)
}

data class HttpCodeResponse(
    internal val statusCode: HttpStatusCode,
    internal val responseTypes: List<ResponseType>,
    internal val description: String? = null
)

@PublishedApi
internal fun singleResponse(
    statusCode: HttpStatusCode,
    responseType: ResponseType,
    description: String? = null
) = HttpCodeResponse(
    statusCode,
    listOf(responseType),
    description
)

fun Metadata.operationId(operationId: String): Metadata =
    copy(operationId = operationId)

fun Metadata.responds(vararg responses: HttpCodeResponse): Metadata =
    copy(responses = (this.responses + responses))

fun Metadata.examples(vararg pairs: Pair<String, Example>): Metadata =
    copy(bodyExamples = (bodyExamples + mapOf(*pairs)))

fun Metadata.description(description: String): Metadata =
    copy(description = description)

fun Metadata.noReflectionBody(name: ModelName): Metadata =
    copy(bodySchema = BodySchema(name))

fun Metadata.security(requirements: Map<String, List<String>>): Metadata =
        copy(requirements = this.requirements?.let { it + requirements } ?: listOf(requirements))
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

fun String.security(
    requirements: Map<String, List<String>>
): Metadata =
    Metadata(requirements = listOf(requirements), summary = this)

fun security(
    requirements: Map<String, List<String>>
): Metadata =
    Metadata(requirements = listOf(requirements))

/**
 * @receiver The summary to use for the operation.
 */
fun String.responds(vararg responses: HttpCodeResponse): Metadata =
    Metadata(responses = listOf(*responses), summary = this)

fun responds(vararg responses: HttpCodeResponse) =
    Metadata(responses = listOf(*responses))

sealed class ResponseType() {
    abstract val examples: Map<String, Example>
}

data class JsonResponseFromReflection
internal constructor(
    val type: TypeInfo,
    override val examples: Map<String, Example>
) : ResponseType() {
    companion object {

        @PublishedApi
        internal fun create(type: TypeInfo, examples: Map<String, Example>) =
            JsonResponseFromReflection(
                type, examples
            )
    }
}

inline fun <reified T> json(vararg examples: Pair<String, Example> = arrayOf()): ResponseType =
    JsonResponseFromReflection.create(typeInfo<T>(), mapOf(*examples))

data class JsonResponseSchema
internal constructor(
    val name: ModelName,
    override val examples: Map<String, Example>
) : ResponseType()

fun json(name: ModelName, vararg examples: Pair<String, Example>): ResponseType =
    JsonResponseSchema(name, mapOf(*examples))

data class CustomContentTypeResponse(val contentType: ContentType) : ResponseType() {
    override val examples: Map<String, Example> = emptyMap()
}

/**
 * The type of the operation body being recived by the server.
 */
sealed class BodyType {
    abstract val examples: Map<String, Example>
}

data class BodyFromString(override val examples: Map<String, Example>) : BodyType()
data class BodyFromReflection(val typeInfo: TypeInfo, override val examples: Map<String, Example>) : BodyType()
data class BodyFromSchema(val name: ModelName, override val examples: Map<String, Example>) : BodyType()

inline fun <reified T> ok(vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    ok(json<T>(*examples))

fun ok(name: String, vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    ok(json(name, *examples))

fun ok(vararg responses: ResponseType = arrayOf(), description: String? = null): HttpCodeResponse =
    HttpCodeResponse(OK, listOf(*responses), description)

inline fun <reified T> noContent(vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    noContent(json<T>(*examples))

fun noContent(name: String, vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    noContent(json(name, *examples))

fun noContent(vararg responses: ResponseType = arrayOf(), description: String? = null): HttpCodeResponse =
    HttpCodeResponse(NoContent, listOf(*responses), description)

inline fun <reified T> created(vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    created(json<T>(*examples))

fun created(name: String, vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    created(json(name, *examples))

fun created(vararg responses: ResponseType = arrayOf(), description: String? = null): HttpCodeResponse =
    HttpCodeResponse(Created, listOf(*responses), description)

fun notFound(): HttpCodeResponse =
    notFound(json<Unit>())

inline fun <reified T> notFound(vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    notFound(json<T>(*examples))

fun notFound(name: String, vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    notFound(json(name, *examples))

fun notFound(vararg responses: ResponseType = arrayOf(), description: String? = null): HttpCodeResponse =
    HttpCodeResponse(NotFound, listOf(*responses), description)

fun badRequest(): HttpCodeResponse =
    badRequest(json<Unit>())

inline fun <reified T> badRequest(vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    badRequest(json<T>(*examples))

fun badRequest(name: String, vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    badRequest(json(name, *examples))

fun badRequest(vararg responses: ResponseType = arrayOf(), description: String? = null): HttpCodeResponse =
    HttpCodeResponse(BadRequest, listOf(*responses), description)

inline fun <reified T> internalServerError(vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    internalServerError(json<T>(*examples))

fun internalServerError(name: String, vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    internalServerError(json(name, *examples))

fun internalServerError(vararg responses: ResponseType = arrayOf(), description: String? = null): HttpCodeResponse =
    HttpCodeResponse(InternalServerError, listOf(*responses), description)

fun resetContent(name: String, vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    HttpStatusCode.ResetContent(json(name, *examples))

fun resetContent(vararg responses: ResponseType = arrayOf(), description: String? = null): HttpCodeResponse =
    HttpStatusCode.ResetContent(*responses, description = description)

operator fun HttpStatusCode.invoke(name: String, vararg examples: Pair<String, Example> = arrayOf()): HttpCodeResponse =
    this(json(name, *examples))

operator fun HttpStatusCode.invoke(vararg responses: ResponseType = arrayOf(), description: String? = null): HttpCodeResponse =
    HttpCodeResponse(this, listOf(*responses), description)

fun contentTypeResponse(contentType: ContentType): ResponseType =
    CustomContentTypeResponse(contentType)

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
inline fun <reified LOCATION : Any, reified ENTITY : Any> Route.patch(
    metadata: Metadata,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(LOCATION, ENTITY) -> Unit
): Route {
    application.swaggerUi.apply {
        metadata.apply<LOCATION, ENTITY>(HttpMethod.Patch)
    }

    return patch<LOCATION> {
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
