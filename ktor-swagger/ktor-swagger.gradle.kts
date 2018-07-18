
dependencies {
    /*
     * Webjars have their resources pakaged in a version specific directory.
     * When this version is bumped, the version in the `SwaggerUi` where the resouce
     * is loaded must also be bumped.
     */
    val swaggerUiVersion = "3.17.4"
    implementation(group = "org.webjars", name = "swagger-ui", version = swaggerUiVersion)
}