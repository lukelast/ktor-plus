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
    implementation(libs.gradleKsp)
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

        inputs.property("version", projectVersion)
        outputs.file(outputFile)

        doLast {
            outputFile.get().asFile.parentFile.mkdirs()

            val libs =
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

            object KtpVersion {
                const val VERSION = "$projectVersion"
                val libs = listOf(${libs.joinToString { "\"$it\"" }})
            }
        """
                        .trimIndent()
                )
        }
    }

sourceSets["main"].java { srcDir(layout.buildDirectory.dir(sourceGenDir)) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { dependsOn(versionGenTask) }

tasks.named("sourcesJar") { dependsOn(versionGenTask) }
