package ktp.example

import ktp.example.api.installApiHello
import ktp.example.plugins.configureAdministration
import net.ghue.ktp.ktor.app.debug.installConfigDebugInfo
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
    init { config ->
        installDefaultPlugins()
        configureAdministration()
        installConfigDebugInfo(config)
        installApiHello()
    }
}

@Module @ComponentScan class DemoAppModule
