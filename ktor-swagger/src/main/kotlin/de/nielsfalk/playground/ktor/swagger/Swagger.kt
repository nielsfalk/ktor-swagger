package de.nielsfalk.playground.ktor.swagger

import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.locations.location
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * @author Niels Falk
 */

class Swagger {
    val swagger = "2.0"
    var info: Map<String, Any>? = null
    val definitions = mutableMapOf<String, Any>()
    val paths = mutableMapOf<String, Any>()
}

val swagger = Swagger()


inline fun <reified LOCATION : Any, reified ENTITY_TYPE : Any> Metadata.apply(method: HttpMethod) {
    val clazz = LOCATION::class.java
    val location = clazz.getAnnotation(location::class.java)
    applyDefinitions()
    applyOperations(location, method, LOCATION::class, ENTITY_TYPE::class)
}

fun Metadata.applyDefinitions() {
    swagger.definitions.putAll(responses.values.toModels())
}

fun <LOCATION : Any, BODY_TYPE : Any> Metadata.applyOperations(location: location, method: HttpMethod, locationType: KClass<LOCATION>, entityType: KClass<BODY_TYPE>) {
    swagger.paths
            .attribute(location.path)
            .attribute(method.value.toLowerCase()).apply {
        put("summary", summary ?: "${method.value} ${location.path}")
        getOrPut("parameters") {
            mutableListOf<MutableMap<String, Any>>().apply {
                addAll(locationType.toList().apply {
                    forEach {
                        it.put("description", it["name"].toString())
                        it.put("in", "path")
                        it.put("required", true)
                    }
                })
                if (entityType != Unit::class) {
                    listOf(entityType).toModels()
                    add(mutableMapOf(
                            "description" to entityType.simpleName!!,
                            "in" to "body",
                            "name" to "body",
                            "required" to true,
                            "schema" to mapOf("\$ref" to "#/definitions/${entityType.simpleName}")
                    ))
                }
            }
        }
        attribute("responses").apply { putAll(responses.toMap()) }
    }
}

private fun Collection<KClass<*>>.toModels() =
        filter { it != Unit::class }
                .map {
                    it.toModel()
                }
                .toMap()

private fun KClass<*>.toModel(): Pair<String, MutableMap<String, Any>> {
    return simpleName!! to mutableMapOf<String, Any>().apply {
        attribute("properties").apply {
            putAll(toProperty())
        }
    }
}

private fun <T : Any> KClass<T>.toProperty(): Map<String, MutableMap<String, Any>> {
    return toList().map { it["name"].toString() to it }.toMap()
}

private fun <T : Any> KClass<T>.toList(): MutableList<MutableMap<String, Any>> =
        this.memberProperties.map {
            attributeToMap(it)
        }.toMutableList()

private fun <T : Any> attributeToMap(it: KProperty1<T, *>): MutableMap<String, Any> =
        when (it.returnType.toString()) {

            "kotlin.Int?" -> integer()
            "kotlin.Int" -> integer()
            "kotlin.String" -> mutableMapOf<String, Any>("type" to "string")
            else -> if (it.returnType.classifier.toString() == "class kotlin.collections.List") {
                val clazz: KClass<*> = it.returnType.arguments.first().type?.classifier as KClass<*>
                val (name, model) = clazz.toModel()
                swagger.definitions.put(name, model)

                mutableMapOf(
                        "type" to "array",
                        "items" to mapOf("\$ref" to "#/definitions/${clazz.simpleName}"))
            } else {
                mutableMapOf()
            }
        }.apply { put("name", it.name) }

private fun integer(): MutableMap<String, Any> = mutableMapOf("type" to "integer", "format" to "int32")


private fun Map<HttpStatusCode, KClass<*>>.toMap() =
        mapValues {
            mutableMapOf<String, Any>("description" to when (it.value) {
                Unit::class -> it.key.description
                else -> it.value.simpleName!!
            }).apply {
                if (it.value != Unit::class) {
                    put("schema", mapOf("\$ref" to "#/definitions/${it.value.simpleName}"))
                }
            }
        }.mapKeys {
            it.key.value.toString()
        }

fun MutableMap<String, Any>.attribute(key: String): MutableMap<String, Any> =
        getOrPut(key) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
