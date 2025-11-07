package net.ghue.ktp.ktor.plugin

import io.ktor.http.*
import kotlin.time.Duration

fun cacheControlMaxAge(
    maxAge: Duration,
    visibility: CacheControl.Visibility = CacheControl.Visibility.Public,
): CacheControl {
    return CacheControl.MaxAge(
        maxAgeSeconds = maxAge.inWholeSeconds.toInt(),
        visibility = visibility,
    )
}
