package net.ghue.ktp.gradle.project

import net.ghue.ktp.gradle.project.mods.*
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
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

    val outputJavaVersion = JavaVersion.VERSION_17

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
}
