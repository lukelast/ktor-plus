package net.ghue.ktp.ktor.plugin

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.matchContentType
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.hsts.HSTS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.resources.Resources
import io.ktor.server.response.respondText
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.error.KtpRspEx
import net.ghue.ktp.ktor.error.processKtpRspEx
import net.ghue.ktp.log.log
import org.slf4j.event.Level

const val MIN_COMPRESS_SIZE_BYTES = 512L

fun Application.installDefaultPlugins(config: KtpConfig) {
    install(MdcClearPlugin)
    install(ContentNegotiation) { json() }
    install(Compression) {
        minimumSize(MIN_COMPRESS_SIZE_BYTES)
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
    install(ConditionalHeaders)
    if (!config.env.isLocalDev) {
        install(HSTS)
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().contains("favicon").not() }
        if (!config.env.isLocalDev) {
            disableDefaultColors()
        }
    }
    install(StatusPages) {
        exception<KtpRspEx>(::processKtpRspEx)
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
    install(CachingHeaders)
}
