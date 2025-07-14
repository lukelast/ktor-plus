package net.ghue.ktp.gradle.project.mods

import net.ghue.ktp.gradle.common.Version
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

fun Project.installJunit() {
    project.dependencies {
        add("testImplementation", platform("org.junit:junit-bom:${Version.JUNIT}"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }
    project.tasks.withType<Test> { useJUnitPlatform() }
}
