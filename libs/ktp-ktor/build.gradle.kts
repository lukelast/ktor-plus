plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(project(":libs:ktp-core"))
    api(project(":libs:ktp-config"))

    // Ktor
    api(platform(libs.ktor.bom))
    api(libs.ktor.core)
    api(libs.ktor.netty)
    api(libs.ktor.serializationJson)
    api(libs.ktor.callLogging)
    api(libs.ktor.statusPages)
    api(libs.ktor.contentNegotiation)
    api(libs.ktor.compression)
    api(libs.ktor.forwardedHeader)
    api(libs.ktor.resources)

    // Ktor Client
    api(libs.ktor.client.core)
    api(libs.ktor.client.apache)

    // Koin
    api(platform(libs.koinBom))
    api(libs.koinAnnotations)
    api(libs.koinCore)
    api(libs.koinKtor)
    implementation(libs.koinLoggerSlf4j)

    // Testing
    testImplementation(libs.ktor.test)
    testImplementation(libs.koinTest)
}
