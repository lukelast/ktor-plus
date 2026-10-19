plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    api(project(":libs:ktp-ktor"))

    api(libs.stripe)

    testImplementation(libs.mockk)
}
