plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(project(":libs:ktp-core"))
    api(libs.typesafeConfig)
    api(libs.config4k)
    implementation(libs.classgraph)
}
