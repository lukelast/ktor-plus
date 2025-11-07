package net.ghue.ktp.ktor.plugin

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.log.log
import org.slf4j.event.Level

fun Application.installDefaultPlugins(config: KtpConfig) {
    install(MdcClearPlugin)
    install(ContentNegotiation) { json() }
    install(Compression) {
        minimumSize(512)
        matchContentType(
            ContentType.Text.Any,
            ContentType.Application.Json,
            ContentType.Application.Xml,
            ContentType.Application.JavaScript,
            ContentType.Image.SVG,
        )
        default()
    }
    install(XForwardedHeaders)
    install(ForwardedHeaders)
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().contains("favicon").not() }
        if (!config.env.isLocalDev) {
            disableDefaultColors()
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log {}
                .warn(cause) {
                    "Unhandled exception in request: ${call.request.path()} " +
                        "with method: ${call.request.httpMethod.value}"
                }
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
    install(Resources)
}
