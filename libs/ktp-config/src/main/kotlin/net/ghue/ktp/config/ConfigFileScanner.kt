package net.ghue.ktp.config

import io.github.classgraph.ClassGraph
import io.github.classgraph.Resource
import net.ghue.ktp.config.KtpConfig.CONF_FILE_DIR
import net.ghue.ktp.config.KtpConfig.CONF_FILE_EXT

/** Find all the config files across the classpath. */
fun scanConfigFiles(): List<ConfigFile> {
    ClassGraph().acceptPathsNonRecursive(CONF_FILE_DIR).scan().use { scanResult ->
        return scanResult
            .getResourcesWithExtension(CONF_FILE_EXT)
            .map { file: Resource -> ConfigFile.create(file.uri.toString(), file.contentAsString) }
            .sorted()
    }
}
