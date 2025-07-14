package ktp.example

import com.mgl.plugins.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import ktp.example.plugins.configureRouting
import ktp.example.plugins.configureSerialization
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.app.debug.installConfigDebugInfo

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
            configureRouting()
            configureHTTP()
            configureSerialization()
            configureAdministration()
            val config = KtpConfig.createManager()
            installConfigDebugInfo(config)
        }
        .start(wait = true)
}
