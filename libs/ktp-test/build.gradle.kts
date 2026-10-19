plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    api(project(":libs:ktp-ktor"))

    api(libs.mockk)
    api(libs.ktor.test)
    api(libs.koin.test)
    api(libs.kotest.koin)
    api(libs.ktor.client.resources)
    api(libs.ktor.client.contentNegotiation)

    implementation(libs.kotest.engine)
}
