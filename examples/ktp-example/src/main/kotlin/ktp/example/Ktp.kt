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
    appInit { config ->
        installDefaultPlugins(config)
        configureAdministration()
        install(ConfigDebugInfoPlugin)
        installApiHello()
    }
}

@Module @ComponentScan class DemoAppModule
