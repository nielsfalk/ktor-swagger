plugins {
    application
}

fun DependencyHandler.ktor(name: String) =
    create(group = "io.ktor", name = name, version = "${property("ktor.version")}")

dependencies {
    implementation(project(":ktor-swagger"))
    implementation(ktor("ktor-server-netty"))
    implementation(ktor("ktor-gson"))
    implementation(group = "com.github.ajalt.clikt", name = "clikt", version = "3.1.0")
}

application {
    mainClass.set("de.nielsfalk.ktor.swagger.sample.JsonApplicationKt")
}
