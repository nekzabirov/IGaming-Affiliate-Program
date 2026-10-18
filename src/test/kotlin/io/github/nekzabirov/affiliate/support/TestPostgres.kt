package io.github.nekzabirov.affiliate.support

import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager

/**
 * One real PostgreSQL 16 for the whole test run, started on first use. Not a @Container field: the Spring
 * context is cached across test classes, and a container stopped after the first class would leave the cached
 * context pointing at nothing.
 */
object TestPostgres {

    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:16-alpine").also { it.start() }
    }

    val jdbcUrl: String get() = container.jdbcUrl

    val username: String get() = container.username

    val password: String get() = container.password

    /** A new empty database in the same server, with the JDBC URL to reach it. */
    fun emptyDatabase(name: String): String {
        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.createStatement().use { it.executeUpdate("CREATE DATABASE \"$name\"") }
        }
        return jdbcUrl.replaceAfterLast('/', name)
    }
}
