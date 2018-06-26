
fun DependencyHandler.ktor(name: String) =
    create(group = "io.ktor", name = name, version = "0.9.2")

dependencies {
    implementation(project(":ktor-swagger"))
    implementation(ktor("ktor-server-netty"))
    implementation(ktor("ktor-gson"))
}