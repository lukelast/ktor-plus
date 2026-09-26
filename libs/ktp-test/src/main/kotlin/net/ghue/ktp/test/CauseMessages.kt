package net.ghue.ktp.test

/** Messages down the cause chain, since Koin and Ktor wrap the failure a test asserts on. */
fun Throwable.causeMessages(): String =
    generateSequence(this) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
