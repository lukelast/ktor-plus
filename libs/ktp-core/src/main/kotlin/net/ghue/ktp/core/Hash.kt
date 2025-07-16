package net.ghue.ktp.core

import java.security.MessageDigest
import kotlin.text.toByteArray

fun String.sha512(): ByteArray {
    val digest = MessageDigest.getInstance("SHA-512").digest(toByteArray(Charsets.UTF_8))
    return digest
}

fun String.sha256(): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest
}
