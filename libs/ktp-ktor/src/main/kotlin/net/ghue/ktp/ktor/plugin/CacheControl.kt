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

/**
 * `max-age` plus the `immutable` extension (RFC 8246), for content-hashed files whose URL changes
 * whenever the bytes do. Browsers then skip revalidation on reload instead of sending a conditional
 * request per file. Ktor's [CacheControl.MaxAge] has no slot for the directive, hence this
 * subclass.
 */
fun cacheControlImmutable(
    maxAge: Duration,
    visibility: CacheControl.Visibility = CacheControl.Visibility.Public,
): CacheControl = ImmutableMaxAge(maxAge.inWholeSeconds, visibility)

private class ImmutableMaxAge(val maxAgeSeconds: Long, visibility: Visibility) :
    CacheControl(visibility) {
    // Same directive order as CacheControl.MaxAge.
    override fun toString(): String {
        val scope = if (visibility == Visibility.Private) "private" else "public"
        return "max-age=$maxAgeSeconds, $scope, immutable"
    }
}
