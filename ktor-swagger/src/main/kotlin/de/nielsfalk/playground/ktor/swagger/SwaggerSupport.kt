package de.nielsfalk.playground.ktor.swagger

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.application.featureOrNull
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.locations.Location
import io.ktor.pipeline.PipelineContext
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.util.AttributeKey
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

class SwaggerSupport(
    val swagger: Swagger
) {
    companion object Feature : ApplicationFeature<Application, SwaggerUiConfiguration, SwaggerSupport> {
        override val key = AttributeKey<SwaggerSupport>("SwaggerSupport")

        override fun install(pipeline: Application, configure: SwaggerUiConfiguration.() -> Unit): SwaggerSupport {
            val (path, forwardRoot, provideUi, swagger) = SwaggerUiConfiguration().apply(configure)
            val feature = SwaggerSupport(swagger)
            pipeline.routing {
                get("/$path") {
                    redirect(path)
                }
                val ui = if (provideUi) SwaggerUi() else null
                get("/$path/{fileName}") {
                    val filename = call.parameters["fileName"]
                    if (filename == "swagger.json") {
                        call.respond(swagger)
                    } else {
                        ui?.serve(filename, call)
                    }
                }
                if (forwardRoot) {
                    get("/") {
                        redirect(path)
                    }
                }
            }
            return feature
        }

        private suspend fun PipelineContext<Unit, ApplicationCall>.redirect(path: String) {
            call.respondRedirect("/$path/index.html?url=swagger.json")
        }
    }

    inline fun <reified LOCATION : Any, reified ENTITY_TYPE : Any> Metadata.apply(method: HttpMethod) {
        val clazz = LOCATION::class.java
        val location = clazz.getAnnotation(Location::class.java)
        val tags = clazz.getAnnotation(Group::class.java)
        applyResponseDefinitions()
        applyOperations(location, tags, method, LOCATION::class, ENTITY_TYPE::class)
    }

    fun <LOCATION : Any, BODY_TYPE : Any> Metadata.applyOperations(
        location: Location,
        group: Group?,
        method: HttpMethod,
        locationType: KClass<LOCATION>,
        entityType: KClass<BODY_TYPE>
    ) {

        fun createOperation(): Operation {
            val responses = responses.map { (status, kClass) ->
                addDefinition(kClass)
                status.value.toString() to Response(status, kClass)
            }.toMap()

            if (entityType != Unit::class) {
                addDefinition(entityType)
            }

            val parameters = mutableListOf<Parameter>().apply {
                if (entityType != Unit::class) {
                    add(entityType.bodyParameter())
                }
                addAll(locationType.memberProperties.map { it.toParameter(location.path).let {
                    addDefintions(it.second)
                    it.first
                } })
                parameter?.let {
                    addAll(it.memberProperties.map { it.toParameter(location.path, ParameterInputType.query).let {
                        addDefintions(it.second)
                        it.first
                    } })
                }
                headers?.let {
                    addAll(it.memberProperties.map { it.toParameter(location.path, ParameterInputType.header).let {
                        addDefintions(it.second)
                        it.first
                    } })
                }
            }

            return Operation(this, responses, parameters, location, group, method, locationType, entityType)
        }

        swagger.paths
            .getOrPut(location.path) { mutableMapOf() }
            .put(
                method.value.toLowerCase(),
                createOperation()
            )
    }

    fun Metadata.applyResponseDefinitions() =
        responses.values.forEach { addDefinition(it) }

    private fun addDefinition(kClass: KClass<*>) {
        if (kClass != Unit::class) {
            val accruedNewDefinitions = mutableListOf<KClass<*>>()
            swagger.definitions.computeIfAbsent(kClass.modelName()) {
                val modelWithAdditionalDefinitions = createModelData(kClass)
                accruedNewDefinitions.addAll(modelWithAdditionalDefinitions.second)
                modelWithAdditionalDefinitions.first
            }

            accruedNewDefinitions.forEach { addDefinition(it) }
        }
    }

    private fun addDefintions(kClasses: Collection<KClass<*>>) = kClasses.forEach { addDefinition(it) }
}

data class SwaggerUiConfiguration(
    var path: String = "apidocs",
    var forwardRoot: Boolean = false,
    var provideUi: Boolean = true,
    var swagger: Swagger = Swagger()
)

fun Application.routing(configure: Routing.() -> Unit) =
    featureOrNull(Routing)?.apply(configure)
        ?: install(Routing, configure)
