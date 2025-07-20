package net.ghue.ktp.log

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import org.slf4j.LoggerFactory

fun installLocalDevConsoleLogger() {
    val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

    // Assuming at most 1 console appender.
    val consoleAppender =
        rootLogger
            .iteratorForAppenders()
            .asSequence()
            .filterIsInstance<ConsoleAppender<*>>()
            .firstOrNull()

    if (consoleAppender != null) {
        val pattern = """%d{HH:mm:ss.SSS} %-5level %logger{36} %mdc - %msg%n"""
        val patternEncode = consoleAppender.encoder as? PatternLayoutEncoder
        if (patternEncode != null) {
            patternEncode.pattern = pattern
            patternEncode.start()
        }
    }
}
