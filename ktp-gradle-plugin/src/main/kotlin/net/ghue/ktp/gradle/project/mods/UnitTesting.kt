package net.ghue.ktp.gradle.project.mods

import net.ghue.ktp.gradle.common.Version
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

fun Project.installKotest() {
    project.dependencies {
        add("testImplementation", platform("io.kotest:kotest-bom:${Version.KOTEST}"))
        add("testImplementation", "io.kotest:kotest-runner-junit5")
        add("testImplementation", "io.kotest:kotest-assertions-core")
        add("testImplementation", "io.kotest:kotest-extensions-koin")
        add("testImplementation", "io.kotest:kotest-assertions-ktor")
    }
    project.tasks.withType<Test> { useJUnitPlatform { includeEngines("kotest") } }
}
