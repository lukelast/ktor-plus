package ktp.example

import io.ktor.server.application.install
import ktp.example.api.installApi
import ktp.example.plugins.configureAdministration
import net.ghue.ktp.ktor.app.debug.ConfigDebugInfoPlugin
import net.ghue.ktp.ktor.plugin.installDefaultPlugins
import net.ghue.ktp.ktor.start.ktpAppCreate
import net.ghue.ktp.ktor.start.start
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.plugin.module.dsl.koinConfiguration

fun main() {
    ktpApp.start()
}

val ktpApp = ktpAppCreate {
    addKoinApp(koinConfiguration<MyApp>())
    appInit { config ->
        installDefaultPlugins(config)
        configureAdministration()
        install(ConfigDebugInfoPlugin)
        installApi()
    }
}

@Module @ComponentScan class DemoAppModule

@KoinApplication(modules = [DemoAppModule::class]) class MyApp
