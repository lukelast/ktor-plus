import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

// https://stackoverflow.com/questions/75673923/build-config-for-developing-a-gradle-plugin-written-in-kotlin
plugins {
    `kotlin-dsl`
    `maven-publish`
}

tasks.validatePlugins { enableStricterValidation.set(true) }

group = "com.github.lukelast.ktor-plus"

version = System.getenv("VERSION") ?: "0-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.gradleKotlin)
    implementation(libs.gradleKtor)
    implementation(libs.gradleKotlinSerialization)
    implementation(libs.gradleKtfmt)
    implementation(libs.gradleDetekt)
    implementation(libs.gradleKoinCompiler)
    // Must be on the runtime classpath (not compileOnly) to win over the older Shadow the
    // Ktor plugin pulls in: 9.1.0 has service-file merge regressions (GradleUp/shadow#1348).
    implementation(libs.gradleShadow)
}

kotlin { jvmToolchain(21) }

java { withSourcesJar() }

gradlePlugin {
    plugins {
        create("ktpGradleProjectPlugin") {
            id = group.toString()
            implementationClass = "net.ghue.ktp.gradle.project.ProjectPlugin"
        }
        create("ktpGradleSettingsPlugin") {
            id = "$group.settings"
            implementationClass = "net.ghue.ktp.gradle.settings.SettingsPlugin"
        }
    }
}

tasks.named<Copy>("processResources") { from("../gradle/libs.versions.toml") { into("") } }

val sourceGenDir = "generated/version"
val versionGenTask =
    tasks.register("generateVersionFile") {
        val outputFile = layout.buildDirectory.file("$sourceGenDir/net/ghue/ktp/lib/Version.kt")
        val projectVersion = project.version.toString()
        val koinVersion = libs.versions.koin.get()
        val kotestVersion = libs.versions.kotest.get()
        val byteBuddyVersion = libs.versions.byteBuddy.get()
        val ktfmtVersion = libs.versions.ktfmt.get()

        inputs.property("version", projectVersion)
        inputs.property("koinVersion", koinVersion)
        inputs.property("kotestVersion", kotestVersion)
        inputs.property("byteBuddyVersion", byteBuddyVersion)
        inputs.property("ktfmtVersion", ktfmtVersion)
        outputs.file(outputFile)

        doLast {
            outputFile.get().asFile.parentFile.mkdirs()

            val libraryNames =
                layout.buildDirectory
                    .dir("../../libs")
                    .get()
                    .asFile
                    .toPath()
                    .listDirectoryEntries()
                    .map { it.name }
            outputFile
                .get()
                .asFile
                .writeText(
                    """
            package net.ghue.ktp.lib

            /** Generated from `gradle/libs.versions.toml`; do not edit by hand. */
            object KtpVersion {
                /** ktor-plus's own published version. */
                const val VERSION = "$projectVersion"

                /** koin BOM version this plugin injects into consumer builds. */
                const val KOIN = "$koinVersion"

                /** kotest BOM version this plugin injects into consumer builds. */
                const val KOTEST = "$kotestVersion"

                /** Byte Buddy agent version this plugin preloads for MockK-friendly tests. */
                const val BYTE_BUDDY = "$byteBuddyVersion"

                /** ktfmt engine version used by the formatting tasks. */
                const val KTFMT = "$ktfmtVersion"

                /** Names of the ktor-plus library modules this plugin publishes. */
                val libs = listOf(${libraryNames.joinToString { "\"$it\"" }})
            }
        """
                        .trimIndent()
                )
        }
    }

sourceSets["main"].java { srcDir(layout.buildDirectory.dir(sourceGenDir)) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { dependsOn(versionGenTask) }

tasks.named("sourcesJar") { dependsOn(versionGenTask) }
