package net.ghue.ktp.ktor.plugin

import com.typesafe.config.ConfigMemorySize
import io.ktor.server.routing.Route
import net.ghue.ktp.config.KtpConfig

val KtpConfig.bodyLimit: BodyLimit
    get() = this.extractChild()

/** The `bodyLimit` config block; see `9.bodyLimit.conf`. */
data class BodyLimit(
    /** Cap for routes without their own [Route.bodyLimit]. */
    val default: ConfigMemorySize
)
