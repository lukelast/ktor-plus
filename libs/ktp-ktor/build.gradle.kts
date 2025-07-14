plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(project(":libs:ktp-core"))
    api(project(":libs:ktp-config"))


    api(platform(libs.ktorBom))
    api(libs.ktorServerCore)
    api(libs.ktorServerMetrics)

    api(platform(libs.koinBom))
    api(libs.koinAnnotations)
    api(libs.koinCore)
    api(libs.koinKtor)
    implementation(libs.koinLoggerSlf4j)

    testImplementation(libs.ktorTest)
    testImplementation(libs.koinTest)
    testImplementation(libs.koinTestJunit5)
}
