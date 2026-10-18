package io.github.nekzabirov.affiliate

import ch.qos.logback.classic.LoggerContext
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import kotlin.system.exitProcess

@SpringBootApplication
@ConfigurationPropertiesScan
class AffiliateApplication {

    @EventListener
    fun started(event: ApplicationReadyEvent) {
        val web = event.applicationContext as? WebServerApplicationContext ?: return
        log.atInfo()
            .addKeyValue("event", "app.started")
            .addKeyValue("port", web.webServer?.port)
            .log("affiliate-app started")
    }

    /** The management server's child context closes too; only the root one is the process stopping. */
    @EventListener
    fun stopping(event: ContextClosedEvent) {
        val context = event.applicationContext
        if (context !is WebServerApplicationContext || context.parent != null) return
        log.atInfo().addKeyValue("event", "app.stopping").log("affiliate-app stopping")
    }
}

private val log = LoggerFactory.getLogger(AffiliateApplication::class.java)

private const val APP = "app"

private const val SCHEMA = "schema"

private const val EXIT_FAILED = 1

private const val EXIT_UNKNOWN_LAUNCHER = 2

/**
 * What the `schema` launcher adds: Flyway on (the service keeps it off so two booting pods never race for the
 * history table), and the broker listener off, so a migration Job never consumes a single event.
 */
private val SCHEMA_ONLY = arrayOf(
    "--spring.flyway.enabled=true",
    "--spring.rabbitmq.listener.simple.auto-startup=false",
)

/** One image, two roles: the first argument picks the launcher, `app` (the default) or `schema` (the migration Job). */
fun main(args: Array<String>) {
    // grpc-netty-shaded prefers its bundled BoringSSL for TLS, and loading it in the alpine image kills the JVM
    // outright (SIGSEGV in netty_internal_tcnative_SSLContext_JNI_OnLoad) without a single Java frame to log. The
    // flag has to be set before any Netty SSL class loads, so it is the first statement, before Spring builds the
    // currency service's channel - the schema launcher builds it too.
    System.setProperty("io.grpc.netty.shaded.io.netty.handler.ssl.noOpenSsl", "true")
    val launcher = args.firstOrNull()?.takeUnless { it.startsWith("-") }
    val springArgs = if (launcher == null) args else args.copyOfRange(1, args.size)
    when (launcher ?: APP) {
        APP -> runApplication<AffiliateApplication>(*springArgs)
        SCHEMA -> exit(schema(springArgs))
        else -> {
            log.atError()
                .addKeyValue("event", "app.unknown_launcher")
                .addKeyValue("launcher", launcher)
                .log("Unknown launcher, expected '$APP' or '$SCHEMA'")
            exit(EXIT_UNKNOWN_LAUNCHER)
        }
    }
}

/** Applies the Flyway migrations and reports the version the schema is at; 0 on success. */
private fun schema(args: Array<String>): Int = try {
    val application = SpringApplication(AffiliateApplication::class.java)
    application.setWebApplicationType(WebApplicationType.NONE)
    application.run(*args, *SCHEMA_ONLY).use { context ->
        val version = context.getBean(Flyway::class.java).info().current()?.version?.version
        log.atInfo()
            .addKeyValue("event", "schema.migrated")
            .addKeyValue("version", version)
            .log("Schema is at version {}", version)
    }
    0
} catch (failure: Exception) {
    log.atError().addKeyValue("event", "schema.failed").setCause(failure).log("Schema migration failed")
    EXIT_FAILED
}

/** The appender is asynchronous: exiting straight away would drop the very lines that say why a launcher failed. */
private fun exit(status: Int): Nothing {
    (LoggerFactory.getILoggerFactory() as? LoggerContext)?.stop()
    exitProcess(status)
}
