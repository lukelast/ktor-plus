package net.ghue.ktp.gradle.project.mods

import org.gradle.api.JavaVersion
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.CommandLineArgumentProvider

internal class NativeAccessArgumentProvider(
    @get:Internal val javaLauncher: Provider<JavaLauncher>
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        if (javaLauncher.majorVersion() >= 24) {
            listOf("--enable-native-access=ALL-UNNAMED")
        } else {
            emptyList()
        }
}

internal class SunMiscUnsafeMemoryAccessArgumentProvider(
    @get:Internal val javaLauncher: Provider<JavaLauncher>
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        if (javaLauncher.majorVersion() >= 23) {
            listOf("--sun-misc-unsafe-memory-access=allow")
        } else {
            emptyList()
        }
}

private fun Provider<JavaLauncher>.majorVersion(): Int =
    orNull?.metadata?.languageVersion?.asInt() ?: JavaVersion.current().majorVersion.toInt()
