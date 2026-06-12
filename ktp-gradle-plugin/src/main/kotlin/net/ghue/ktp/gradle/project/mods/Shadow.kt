package net.ghue.ktp.gradle.project.mods

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.kotlin.dsl.withType

fun Project.configureShadow() {
    // Configure Shadow tasks after they are created by the Ktor plugin
    tasks.withType<ShadowJar>().configureEach {
        // Enable ZIP64 format to support archives with >65,535 entries
        isZip64 = true
        // Merge META-INF/services files instead of keeping an arbitrary first occurrence.
        // Several libraries split one service across jars: gRPC declares its load balancers
        // (pick_first in grpc-core, round_robin in grpc-util) in io.grpc.LoadBalancerProvider
        // files, and dropping one fails at runtime with "Could not find policy 'pick_first'".
        // Shadow 9.x duplicate handling can drop entries before transformers see them
        // (GradleUp/shadow#1348), so let every entry through to the transformer.
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
    }
}
