package net.ghue.ktp.ktor.plugin

import io.ktor.http.ContentType
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
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.hsts.HSTS
import io.ktor.server.request.path
import io.ktor.server.resources.Resources
import net.ghue.ktp.config.KtpConfig
import org.slf4j.event.Level

const val MIN_COMPRESS_SIZE_BYTES = 512L

fun Application.installDefaultPlugins(config: KtpConfig) {
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
    // GCP appends the real client IP last to X-Forwarded-For without sanitizing earlier ones, so
    // useFirstProxy() is spoofable; ForwardedHeaders is omitted as GCP never sanitizes Forwarded.
    install(XForwardedHeaders) { useLastProxy() }
    install(ConditionalHeaders)
    if (!config.env.isLocalDev) {
        install(HSTS)
    }
    install(SecurityHeadersPlugin) { ktpConfig = config }
    installBodyLimit(config)
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().contains("favicon").not() }
        if (!config.env.isLocalDev) {
            disableDefaultColors()
        }
    }
    installStatusPages()
    install(Resources)
    install(CachingHeaders)
}
