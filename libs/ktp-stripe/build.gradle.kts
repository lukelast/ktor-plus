plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    api(project(":libs:ktp-ktor"))

    api(platform(libs.ktor.bom))
    api(platform(libs.koinBom))

    api(libs.stripe)
    implementation(libs.kotlinJson)

    testImplementation(libs.mockk)
}
