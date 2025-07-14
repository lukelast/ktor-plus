package ktp.example

import io.ktor.server.application.*
import ktp.example.plugins.configureAdministration
import ktp.example.plugins.configureHTTP
import ktp.example.plugins.configureRouting
import ktp.example.plugins.configureSerialization
import net.ghue.ktp.config.KtpConfigManager
import net.ghue.ktp.ktor.app.debug.installConfigDebugInfo
import net.ghue.ktp.ktor.start.ktpStart
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton
import org.koin.ksp.generated.module

fun main() {
    ktpStart(koinModule = { includes(DemoAppModule().module) })
}

@Singleton(createdAtStart = true)
class AppInit(app: Application, config: KtpConfigManager) {
    init {
        with(app) {
            configureRouting()
            configureHTTP()
            configureSerialization()
            configureAdministration()
            installConfigDebugInfo(config)
        }
    }
}

@Module @ComponentScan class DemoAppModule
