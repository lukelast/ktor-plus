plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(project(":libs:ktp-ktor"))

    api(libs.mockk)
    api(libs.ktor.test)
    api(libs.koinTest)

    implementation(libs.kotest.engine)
}
