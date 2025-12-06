package net.ghue.ktp.gradle.project.mods

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType

fun Project.configureShadow() {
    // Configure Shadow tasks after they are created by the Ktor plugin
    tasks.withType<ShadowJar>().configureEach {
        // Enable ZIP64 format to support archives with >65,535 entries
        isZip64 = true
    }
}
