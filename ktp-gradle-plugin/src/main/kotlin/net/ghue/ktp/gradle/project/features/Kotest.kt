package net.ghue.ktp.gradle.project.features

import net.ghue.ktp.lib.KtpVersion
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.process.CommandLineArgumentProvider

private const val TEST_JAVA_AGENT_CONFIGURATION = "ktpTestJavaAgent"

internal fun Project.configureKotest() {
    val testJavaAgent =
        configurations.maybeCreate(TEST_JAVA_AGENT_CONFIGURATION).apply {
            description = "Java agents loaded at startup for KTP-managed test JVMs."
            isCanBeConsumed = false
            isCanBeResolved = true
        }

    project.dependencies {
        add(TEST_JAVA_AGENT_CONFIGURATION, "net.bytebuddy:byte-buddy-agent:${KtpVersion.BYTE_BUDDY}")
        add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, "io.kotest:kotest-runner-junit5")
        add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, "io.kotest:kotest-assertions-core")
        add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, "io.kotest:kotest-extensions-koin")
        add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, "io.kotest:kotest-assertions-ktor")
    }
    project.tasks.withType<Test>().configureEach {
        // Avoid dynamic Java agent loading warnings from MockK's Byte Buddy agent.
        jvmArgumentProviders.add(JavaAgentArgumentProvider(testJavaAgent))
        // Avoid JDK 23+ warnings for terminally deprecated sun.misc.Unsafe memory access.
        jvmArgumentProviders.add(SunMiscUnsafeMemoryAccessArgumentProvider(javaLauncher))
        // Avoid CDS warnings when test agents append to the bootstrap classpath.
        jvmArgs("-Xshare:off")
        useJUnitPlatform {
            includeEngines("kotest")
            excludeTags("integration")
        }
        // Runs test classes in separate JVM processes
        maxParallelForks = 1
        systemProperties =
            mapOf(
                "kotest.framework.config.fqn" to "net.ghue.ktp.test.config.ProjectConfigUnit",
            )
    }

    project.tasks.register<Test>("integrationTest") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs integration tests."

        useJUnitPlatform {
            includeEngines("kotest")
            includeTags("integration")
        }
        maxParallelForks = 1
        systemProperties =
            mapOf(
                "kotest.framework.config.fqn" to
                    "net.ghue.ktp.test.config.ProjectConfigIntegration",
            )
    }
}

private class JavaAgentArgumentProvider(
    @get:Classpath val agentClasspath: FileCollection
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        agentClasspath.files.map { "-javaagent:${it.absolutePath}" }
}
