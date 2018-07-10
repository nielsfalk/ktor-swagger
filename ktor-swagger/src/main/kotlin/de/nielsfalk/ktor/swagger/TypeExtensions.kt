package de.nielsfalk.ktor.swagger

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Used for looking up the [Type] of a generic type name like `T`.
 */
internal fun ParameterizedType.typeForName(name: String): Type =
    actualTypeArguments[indexForName(name)]

private fun ParameterizedType.indexForName(name: String) =
    (rawType as Class<*>).typeParameters.indexOfFirst { it.name == name }
