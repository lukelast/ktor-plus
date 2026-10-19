package net.ghue.ktp.config

import io.github.classgraph.ClassGraph
import io.github.classgraph.Resource

/** Find all the config files across the classpath. */
fun scanConfigFiles(): List<ConfigFile> {
    // ClassGraph 6 scans nothing until told where: the classpath and the app's own modules.
    ClassGraph()
        .enableClasspath()
        .enableNonSystemModules()
        .acceptPathsNonRecursive(KtpConfig.CONFIG_FILE_DIR)
        .scan()
        .use { scanResult ->
            return scanResult
                .getResourcesWithExtension(KtpConfig.CONFIG_FILE_EXT)
                .map { file: Resource ->
                    ConfigFile.create(file.uri.toString(), file.loadAsString())
                }
                .sorted()
        }
}
