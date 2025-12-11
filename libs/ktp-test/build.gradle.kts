plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(project(":libs:ktp-ktor"))

    api(platform(libs.kotest.bom))
    api(libs.mockk)
    api(libs.ktor.test)
    api(libs.koinTest)
    api(libs.kotest.koin)
    api(libs.ktor.client.resources)
    api(libs.ktor.client.contentNegotiation)

    implementation(libs.kotest.engine)
}
