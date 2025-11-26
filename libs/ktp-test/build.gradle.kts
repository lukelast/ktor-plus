plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(project(":libs:ktp-ktor"))

    api(platform(libs.kotest.bom))
    api(libs.mockk)
    api(libs.ktor.test)
    api(libs.koinTest)
    api(libs.kotest.koin)

    implementation(libs.kotest.engine)
}
