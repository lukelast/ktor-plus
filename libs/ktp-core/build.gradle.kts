plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(libs.kotlinReflect)
    api(libs.kotlinJson)

    api(libs.logbackClassic)

    // https://github.com/oshai/kotlin-logging
    api(libs.kotlinLogging)

    implementation(libs.slf4jJcl)
    implementation(libs.slf4jJul)
}
