package io.github.nekzabirov.affiliate.support

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/**
 * The lines one logger writes while a test runs, as a log store would get their keys: production logs are queried by
 * `event` and its key-values, so that is what a test checks. Lowers the logger to INFO for the while, since the
 * test configuration keeps the root at WARN.
 */
class CapturedLog(type: Class<*>) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(type) as Logger

    private val previous: Level? = logger.level

    private val appender = ListAppender<ILoggingEvent>().apply { start() }

    init {
        logger.level = Level.INFO
        logger.addAppender(appender)
    }

    /** The key-values of every line written with this [event], in order. */
    fun lines(event: String): List<Map<String, Any?>> =
        appender.list
            .map { line -> line.keyValuePairs.orEmpty().associate { it.key to it.value } }
            .filter { it[EVENT] == event }

    override fun close() {
        logger.detachAppender(appender)
        logger.level = previous
    }

    private companion object {

        const val EVENT = "event"
    }
}
