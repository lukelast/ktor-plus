package net.ghue.ktp.gradle.project.mods

import net.ghue.ktp.lib.KtpVersion
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider

private const val TEST_JAVA_AGENT_CONFIGURATION = "ktpTestJavaAgent"

fun Project.installKotest() {
    val testJavaAgent =
        configurations.maybeCreate(TEST_JAVA_AGENT_CONFIGURATION).apply {
            description = "Java agents loaded at startup for KTP-managed test JVMs."
            isCanBeConsumed = false
            isCanBeResolved = true
        }

    project.dependencies {
        add(TEST_JAVA_AGENT_CONFIGURATION, "net.bytebuddy:byte-buddy-agent:${KtpVersion.BYTE_BUDDY}")
        add("testImplementation", platform("io.kotest:kotest-bom:${KtpVersion.KOTEST}"))
        add("testImplementation", "io.kotest:kotest-runner-junit5")
        add("testImplementation", "io.kotest:kotest-assertions-core")
        add("testImplementation", "io.kotest:kotest-extensions-koin")
        add("testImplementation", "io.kotest:kotest-assertions-ktor")
    }
    project.tasks.withType<Test>().configureEach {
        // Avoid dynamic Java agent loading warnings from MockK's Byte Buddy agent.
        jvmArgumentProviders.add(ByteBuddyAgentArgumentProvider(testJavaAgent))
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
        group = "verification"
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

private class ByteBuddyAgentArgumentProvider(
    @get:Classpath val agentClasspath: FileCollection
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        agentClasspath.files.map { "-javaagent:${it.absolutePath}" }
}

private class SunMiscUnsafeMemoryAccessArgumentProvider(
    @get:Internal val javaLauncher: Provider<JavaLauncher>
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        val javaMajorVersion =
            javaLauncher.orNull?.metadata?.languageVersion?.asInt()
                ?: JavaVersion.current().majorVersion.toInt()

        return if (javaMajorVersion >= 23) {
            listOf("--sun-misc-unsafe-memory-access=allow")
        } else {
            emptyList()
        }
    }
}
