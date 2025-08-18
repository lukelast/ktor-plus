plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(project(":libs:ktp-ktor"))

    api(platform(libs.junitBom))
    api("org.junit.jupiter:junit-jupiter-api")
    api("org.junit.jupiter:junit-jupiter-engine")
    api("org.junit.jupiter:junit-jupiter-params")

    api(libs.ktor.test)

    api(platform(libs.koinBom))
    api(libs.koinTest)
    api(libs.koinTestJunit5)

    api(libs.mockitoKotlin)
}
