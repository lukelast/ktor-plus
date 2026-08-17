plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    api(libs.kotlinReflect)
    api(libs.kotlinJson)

    api(libs.caffeine)

    api(libs.logbackClassic)
    api(libs.logstashLogbackEncoder)

    // https://github.com/oshai/kotlin-logging
    api(libs.kotlinLogging)

    api(libs.slf4jJcl)
    api(libs.slf4jJul)
}
