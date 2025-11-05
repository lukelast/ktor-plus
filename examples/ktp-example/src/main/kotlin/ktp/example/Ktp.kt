package ktp.example

import io.ktor.server.application.*
import ktp.example.api.installApiHello
import ktp.example.plugins.configureAdministration
import net.ghue.ktp.ktor.app.debug.ConfigDebugInfoPlugin
import net.ghue.ktp.ktor.plugin.installDefaultPlugins
import net.ghue.ktp.ktor.start.ktpAppCreate
import net.ghue.ktp.ktor.start.start
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.ksp.generated.module

fun main() {
    ktpApp.start()
}

val ktpApp = ktpAppCreate {
    addModule(DemoAppModule().module)
    init {
        installDefaultPlugins()
        configureAdministration()

        // Install debug info plugin with modern API and access control
        install(ConfigDebugInfoPlugin) {
            // Example: Restrict access to localhost only (uncomment to enable)
            // accessControl = { request.local.remoteHost == "127.0.0.1" }
        }

        installApiHello()
    }
}

@Module @ComponentScan class DemoAppModule
