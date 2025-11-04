plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.versionCheck)
}

subprojects {
    if (projectDir.parentFile.name == "libs") {
        group = "com.github.lukelast.ktor-plus"
        ext["ktp.mode"] = "library"
    }
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf {
        fun String.isNonStable() = "^[0-9,.v-]+(-r)?$".toRegex().matches(this).not()
        candidate.version.isNonStable()
    }
}

tasks.register<Delete>("clean") { delete(rootProject.layout.buildDirectory) }

tasks.register("check") {
    description = "Runs all the tests/verification tasks on all projects."
    dependsOn(gradle.includedBuild("ktp-gradle-plugin").task(":check"))
    dependsOn(gradle.includedBuild("ktp-gradle-plugin").task(":validatePlugins"))
}

tasks.register("publishToMavenLocal") {
    dependsOn(gradle.includedBuild("ktp-gradle-plugin").task(":publishToMavenLocal"))
    subprojects.forEach { subproject ->
        if (subproject.projectDir.parentFile.name == "libs") {
            dependsOn(subproject.tasks.named("publishToMavenLocal"))
        }
    }
}

tasks.register("publish") {
    description = "Publishes libraries and plugins to Nexus"
    dependsOn(gradle.includedBuild("ktp-gradle-plugin").task(":publish"))
}

// Download the Gradle source for debugging.
tasks.wrapper { distributionType = Wrapper.DistributionType.ALL }
