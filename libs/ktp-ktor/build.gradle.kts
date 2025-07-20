plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(project(":libs:ktp-core"))
    api(project(":libs:ktp-config"))

    // Ktor
    api(platform(libs.ktorBom))
    api(libs.ktorServerCore)
    api(libs.ktorServerNetty)
    // Ktor Client
    api(libs.ktorClientCore)
    api(libs.ktorClientApache)

    // Koin
    api(platform(libs.koinBom))
    api(libs.koinAnnotations)
    api(libs.koinCore)
    api(libs.koinKtor)
    implementation(libs.koinLoggerSlf4j)

    // Testing
    testImplementation(libs.ktorTest)
    testImplementation(libs.koinTest)
    testImplementation(libs.koinTestJunit5)
}
