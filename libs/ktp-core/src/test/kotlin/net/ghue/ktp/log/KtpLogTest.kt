package net.ghue.ktp.log

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec

class KtpLogTest :
    StringSpec({
        "log can write messages without throwing" { shouldNotThrowAny { log {}.info { "Hi" } } }
    })
