package net.ghue.ktp.ktor.app.debug

import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.ghue.ktp.config.KtpConfig
import org.koin.ktor.ext.inject

suspend fun RoutingCall.respondVersion() {
    val ktpConfig: KtpConfig by inject()
    respondText(ktpConfig.data.app.version)
}
