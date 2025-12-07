package net.ghue.ktp.core.string

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Standard URL decode. */
fun String.decodeUrlEncoded(): String =
    try {
        URLDecoder.decode(this, StandardCharsets.UTF_8)
    } catch (ex: IllegalArgumentException) {
        throw IllegalArgumentException("Could not URL decode encoded string '$this'", ex)
    }

/** Remove characters from a string. */
fun String.remove(vararg chars: Char): String = this.filter { it !in chars }

fun String.toSnakeCase(): String {
    return this.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
}

fun String.toGzip(): ByteArray {
    val bos = ByteArrayOutputStream()
    GZIPOutputStream(bos).use { it.write(this.toByteArray()) }
    return bos.toByteArray()
}

fun ByteArray.unGzip(): String {
    val bais = ByteArrayInputStream(this)
    val uncompressed = GZIPInputStream(bais).use { it.readBytes() }
    return String(uncompressed)
}
