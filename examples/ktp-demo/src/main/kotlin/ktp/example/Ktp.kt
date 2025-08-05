package ktp.example

import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import ktp.example.plugins.configureAdministration
import ktp.example.plugins.configureHTTP
import ktp.example.plugins.configureRouting
import ktp.example.plugins.configureSerialization
import net.ghue.ktp.ktor.app.debug.installConfigDebugInfo
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
        configureRouting()
        configureHTTP()
        configureSerialization()
        configureAdministration()
        installConfigDebugInfo(config)
        routing { get("/") { call.respondText("KTP is running!") } }
    }
}

@Module @ComponentScan class DemoAppModule
