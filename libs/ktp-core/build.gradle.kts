plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(libs.kotlinReflect)
    api(libs.kotlinJson)

    api(libs.logbackClassic)

    // https://github.com/oshai/kotlin-logging
    api(libs.kotlinLogging)

    api(libs.slf4jJcl)
    api(libs.slf4jJul)
}
