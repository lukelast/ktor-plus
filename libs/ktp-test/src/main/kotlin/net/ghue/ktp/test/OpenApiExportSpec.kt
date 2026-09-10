package net.ghue.ktp.test

import io.kotest.core.spec.style.FunSpec
import io.ktor.server.application.Application
import java.io.File
import net.ghue.ktp.ktor.openapi.openApiDocument
import net.ghue.ktp.ktor.start.KtpAppBuilderFactory
import net.ghue.ktp.ktor.start.update

/**
 * Boots the app with the unit-test config and writes its OpenAPI contract where the KTP Gradle
 * plugin's openApiExport task expects it. Subclass as `OpenApiExportTest` so that task finds it.
 */
abstract class OpenApiExportSpec(appFactory: KtpAppBuilderFactory) :
    FunSpec({
        test("export the OpenAPI contract") {
            // Runs after the app's own inits, so every route is installed.
            val export = appFactory.update { addAppInit { exportOpenApi() } }
            ktpTestApp(export) {}
        }
    })

private fun Application.exportOpenApi() {
    File("build/openapi/openapi.json").apply {
        parentFile.mkdirs()
        writeText(openApiDocument())
    }
}
