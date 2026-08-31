package net.ghue.ktp.lib

import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the generated [KtpLibs] contract. The accessor names are public API that consumers
 * write in build scripts, so a change to the generator's alias rule, coordinates, or module
 * coverage must fail here instead of silently renaming accessors in a release.
 */
class KtpLibsTest {

    /** The alias a library module's accessor must use: `ktp-gcp-auth` -> `gcpAuth`. */
    private fun aliasFor(module: String) =
        module
            .removePrefix("ktp-")
            .split('-')
            .mapIndexed { i, part ->
                if (i == 0) part else part.replaceFirstChar { it.uppercaseChar() }
            }
            .joinToString("")

    /** The published library modules, found the same way the generator finds them. */
    private val libraryModules =
        File("../libs")
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.resolve("build.gradle.kts").exists() }
            .map { it.name }
            .sorted()

    /** Every String constant on the generated object, by name. */
    private val accessors: Map<String, String> =
        KtpLibs::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .associate { it.name to it.get(null) as String }

    @Test
    fun `every library module has an accessor with its exact coordinates`() {
        assertTrue(
            libraryModules.isNotEmpty(),
            "No modules found in ../libs; tests must run with ktp-gradle-plugin as working dir.",
        )
        libraryModules.forEach { module ->
            assertEquals(
                "com.github.lukelast.ktor-plus:$module:${KtpVersion.VERSION}",
                accessors[aliasFor(module)],
                "accessor '${aliasFor(module)}' for module '$module'",
            )
        }
    }

    @Test
    fun `no accessors beyond koinBom and the library modules`() {
        assertEquals(
            (libraryModules.map { aliasFor(it) } + "koinBom").sorted(),
            accessors.keys.sorted(),
        )
    }

    @Test
    fun `koinBom carries the koin version from the version catalog`() {
        val koinVersion =
            Regex("""(?m)^koin\s*=\s*"([^"]+)"""")
                .find(File("../gradle/libs.versions.toml").readText())
                ?.groupValues
                ?.get(1)
        assertEquals("io.insert-koin:koin-bom:$koinVersion", accessors["koinBom"])
    }
}
