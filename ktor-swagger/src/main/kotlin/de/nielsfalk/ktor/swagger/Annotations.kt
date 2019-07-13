package de.nielsfalk.ktor.swagger

@Target(AnnotationTarget.PROPERTY)
annotation class DefaultValue(
    val value: String
)

@Target(AnnotationTarget.PROPERTY)
annotation class Description(
    val description: String
)

@Target(AnnotationTarget.PROPERTY)
annotation class Ignore
