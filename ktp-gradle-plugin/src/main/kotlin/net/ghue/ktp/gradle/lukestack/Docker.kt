package net.ghue.ktp.gradle.lukestack

import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register

private const val DOCKER_GROUP = "docker"
private const val HOST_PORT = 80
private const val CONTAINER_PORT = 8080

/**
 * Registers tasks that build and run the app's Docker image. The image is named after the root
 * project and built from the `deploy/Dockerfile` convention location.
 */
internal fun Project.registerDockerTasks() {
    val imageName = rootProject.name
    val dockerBuild =
        tasks.register<Exec>("dockerBuild") {
            group = DOCKER_GROUP
            description = "Builds the Docker image for the application."
            workingDir = projectDir
            commandLine("docker", "build", "--tag", imageName, "--file", "deploy/Dockerfile", ".")
        }
    val dockerRun =
        tasks.register<Exec>("dockerRun") {
            group = DOCKER_GROUP
            description = "Runs the Docker image for the application."
            workingDir = projectDir
            mustRunAfter(dockerBuild)
            commandLine(
                "docker",
                "run",
                "--env",
                "KTP_ENV=docker",
                "--publish",
                "$HOST_PORT:$CONTAINER_PORT",
                "--rm",
                imageName,
            )
        }
    tasks.register("docker") {
        group = DOCKER_GROUP
        description = "Builds and runs the Docker image."
        dependsOn(dockerBuild, dockerRun)
    }
}
