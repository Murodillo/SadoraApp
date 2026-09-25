package uz.sadora.server.core

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Bridges between the three time types in play: `kotlin.time.Instant` in the domain,
 * `java.time.OffsetDateTime` at the JDBC boundary (every timestamp column is
 * `timestamptz`), and `kotlinx.datetime.LocalDate` for calendar days.
 *
 * Conversions go through epoch seconds rather than a stdlib bridge so they do not depend
 * on which `toJavaInstant` extension happens to be on the classpath.
 */
fun now(): Instant = Clock.System.now()

fun Instant.toOffsetDateTime(): OffsetDateTime =
    OffsetDateTime.ofInstant(
        java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong()),
        ZoneOffset.UTC,
    )

fun OffsetDateTime.toKotlinInstant(): Instant =
    Instant.fromEpochSeconds(toEpochSecond(), toInstant().nano)

/**
 * The calendar day an instant falls on for a given user. Usage limits reset at the
 * user's midnight, so this — not the server's clock — decides which day a call counts to.
 */
fun Instant.dayIn(timezone: String): LocalDate =
    toLocalDateTime(resolveTimeZone(timezone)).date

/** Falls back to Tashkent when a client sends a timezone the JVM does not recognise. */
fun resolveTimeZone(id: String): TimeZone =
    runCatching { TimeZone.of(id) }.getOrElse { TimeZone.of(DEFAULT_TIMEZONE) }

fun isValidTimeZone(id: String): Boolean =
    runCatching { ZoneId.of(id) }.isSuccess

const val DEFAULT_TIMEZONE: String = "Asia/Tashkent"
