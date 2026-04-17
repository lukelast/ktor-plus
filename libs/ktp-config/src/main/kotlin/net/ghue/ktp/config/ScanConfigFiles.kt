package net.ghue.ktp.config

import io.github.classgraph.ClassGraph
import io.github.classgraph.Resource

/** Find all the config files across the classpath. */
fun scanConfigFiles(): List<ConfigFile> {
    ClassGraph().acceptPathsNonRecursive(KtpConfig.CONFIG_FILE_DIR).scan().use { scanResult ->
        return scanResult
            .getResourcesWithExtension(KtpConfig.CONFIG_FILE_EXT)
            .map { file: Resource -> ConfigFile.create(file.uri.toString(), file.contentAsString) }
            .sorted()
    }
}
