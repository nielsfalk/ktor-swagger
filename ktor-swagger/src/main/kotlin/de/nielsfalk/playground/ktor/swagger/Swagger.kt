package de.nielsfalk.playground.ktor.swagger

import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.locations.location
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

/**
 * @author Niels Falk
 */

val swagger = JSONObject().apply {
    put("swagger", "2.0")
}

inline fun <reified LOCATION : Any, reified ENTITY_TYPE : Any> Metadata.apply(method: HttpMethod) {
    val clazz = LOCATION::class.java
    val location = clazz.getAnnotation(location::class.java)
    applyDefinitions()
    applyOperations(location, method, LOCATION::class, ENTITY_TYPE::class)
}

fun Metadata.applyDefinitions() {
    swagger.attribute("definitions").apply {
        putAll(responses.values.toModels())
    }
}

fun <LOCATION : Any, BODY_TYPE : Any> Metadata.applyOperations(location: location, method: HttpMethod, locationType: KClass<LOCATION>, entityType: KClass<BODY_TYPE>) {
    swagger.attribute("paths")
            .attribute(location.path)
            .attribute(method.value.toLowerCase()).apply {
        put("summary", summary?:"${method.value} ${location.path}")
        getOrPut("parameters") {
            JSONArray().apply {
                addAll(locationType.nameToJson().map { (name, json) ->
                    json.apply {
                        put("description", name)
                        put("in", "path")
                        put("name", name)
                        put("required", true)
                    }
                })
                if (entityType != Unit::class) {
                    listOf(entityType).toModels()
                    add(JSONObject().apply {
                        put("description", entityType.simpleName)
                        put("in", "body")
                        put("name", "body")
                        put("required", true)
                        attribute("schema").apply {
                            put("\$ref", "#/definitions/${entityType.simpleName}")
                        }
                    })
                }
            }
        }
        attribute("responses").apply { putAll(responses.toSwagger()) }
    }

}


private fun Collection<KClass<*>>.toModels() =
        filter { it != Unit::class }
                .map {
                    it.toModel()
                }
                .toMap()

private fun KClass<*>.toModel(): Pair<String?, JSONObject> {
    return simpleName to JSONObject().apply {
        attribute("properties").apply {
            putAll(toProperty())
        }
    }
}

private fun <T : Any> KClass<T>.toProperty(): Map<String, JSONObject> {
    return nameToJson().toMap()
}

private fun <T : Any> KClass<T>.nameToJson(): List<Pair<String, JSONObject>> {
    return this.memberProperties.map {
        it.name to when (it.returnType.toString()) {

            "kotlin.Int?" -> JSONObject().apply {
                put("type", "integer")
                put("format", "int32")
            }
            "kotlin.Int" -> JSONObject().apply {
                put("type", "integer")
                put("format", "int32")
            }
            "kotlin.String" -> JSONObject().apply {
                put("type", "string")
            }
        //todo: niels 22.08.2017: list
            else -> if (it.returnType.classifier.toString().equals("class kotlin.collections.List")) {
                val clazz: KClass<*> = it.returnType.arguments.first().type?.classifier as KClass<*>
                swagger.attribute("definitions").apply {
                    val (name, model) = clazz.toModel()
                    put(name, model)
                }

                JSONObject().apply {
                    put("type" , "array")
                    attribute("items").apply {
                        put("\$ref","#/definitions/${clazz.simpleName}");
                    }
                }
            } else {
                JSONObject()
            }
        }
    }
}


private fun Map<HttpStatusCode, KClass<*>>.toSwagger() =
        mapValues {
            JSONObject().apply {
                put("description", when (it.value) {
                    Unit::class -> it.key.description
                    else -> it.value.simpleName
                })
                if (it.value != Unit::class) {
                    attribute("schema").apply {
                        put("\$ref", "#/definitions/${it.value.simpleName}")
                    }
                }
            }
        }.mapKeys {
            it.key.value.toString()
        }

fun JSONObject.attribute(key: String) = getOrPut(key) { JSONObject() } as JSONObject
