package io.github.nekzabirov.affiliate.db

import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

// Column readers shared by the stores. pgjdbc maps timestamptz to OffsetDateTime and date to LocalDate exactly,
// whatever the JVM time zone is; java.sql.Timestamp and java.sql.Date would shift by it.

internal fun ResultSet.instant(column: String): Instant = getObject(column, OffsetDateTime::class.java).toInstant()

internal fun ResultSet.instantOrNull(column: String): Instant? = getObject(column, OffsetDateTime::class.java)?.toInstant()

internal fun ResultSet.localDate(column: String): LocalDate = getObject(column, LocalDate::class.java)

internal fun ResultSet.longOrNull(column: String): Long? = getLong(column).takeUnless { wasNull() }

internal fun ResultSet.intOrNull(column: String): Int? = getInt(column).takeUnless { wasNull() }

/** pgjdbc binds OffsetDateTime as timestamptz; it has no binding for Instant. */
internal fun Instant.utc(): OffsetDateTime = atOffset(ZoneOffset.UTC)

/** A LIKE pattern matching [text] anywhere, with the pattern characters in it taken literally (ESCAPE '\'). */
internal fun containsPattern(text: String): String =
    "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

/** The row a page starts at; a Long, because page * size can pass Int.MAX_VALUE on a hand-written URL. */
internal fun offset(page: Int, size: Int): Long = page.toLong() * size
