package net.ghue.ktp.gradle.project

import net.ghue.ktp.gradle.project.mods.*
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun Project.applyLibrary() {
    applyKotlin()
    pluginManager.apply(JavaLibraryPlugin::class.java)
    pluginManager.apply(MavenPublishPlugin::class.java)
    applyKtfmt()

    // Virtual threads require 21+.
    val outputJavaVersion = JavaVersion.VERSION_21

    // Configure Java to include source JAR
    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        sourceCompatibility = outputJavaVersion
        targetCompatibility = outputJavaVersion
    }

    project.tasks.withType<KotlinCompile>().configureEach {
        compilerOptions { jvmTarget.set(JvmTarget.fromTarget(outputJavaVersion.majorVersion)) }
    }

    configPublishJava()

    installKotest()
}

fun Project.configPublishJava() {
    extensions.configure<PublishingExtension> {
        publications {
            publications.create<MavenPublication>("mavenJava") {
                from(components.getByName("java"))
            }
        }
    }

    // Disable Gradle Module Metadata on JitPack builds only. JitPack rewrites the
    // server-side .module file and strips the `-sources` classifier from the
    // sourcesElements file entry, which causes IntelliJ to attach the binary jar
    // as the SOURCES root and source navigation breaks for consumers.
    // Without GMM, Gradle falls back to POM-based resolution, where the sources
    // classifier convention is hardcoded and JitPack serves it correctly.
    // See https://github.com/jitpack/jitpack.io/issues/4476
    if (System.getenv("JITPACK") != null) {
        tasks.withType<GenerateModuleMetadata>().configureEach { enabled = false }
    }
}
