plugins { id("com.github.lukelast.ktor-plus.project") }

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
    api(libs.ktor.cachingHeaders)
    api(libs.ktor.conditionalHeaders)
    api(libs.ktor.hsts)
    api(libs.ktor.bodyLimit)
    api(libs.ktor.resources)

    // Ktor Client
    api(libs.ktor.client.core)
    api(libs.ktor.client.java)

    // Koin
    api(platform(libs.koin.bom))
    api(libs.koin.annotations)
    api(libs.koin.core)
    api(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Testing
    testImplementation(libs.ktor.test)
    testImplementation(libs.koin.test)
}
