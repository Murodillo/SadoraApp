package org.example.project.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Number and date formatting for the Uzbek locale used throughout the design:
 * thousands are separated by a space ("1 240") and decimals use a comma ("1,2").
 */
object Fmt {

    /** 1240 -> "1 240" */
    fun int(value: Int): String {
        val digits = value.toString()
        val sign = if (digits.startsWith("-")) "-" else ""
        val body = digits.removePrefix("-")
        return sign + body.reversed().chunked(3).joinToString(" ").reversed()
    }

    /** 1.24 -> "1,2" (one decimal place). */
    fun oneDecimal(value: Float): String {
        val scaled = kotlin.math.round(value * 10).toInt()
        return "${scaled / 10},${scaled % 10}"
    }

    /** 1200 ml -> "1,2" litres. */
    fun litres(ml: Int): String = oneDecimal(ml / 1000f)

    /** Uzbek month names, nominative. */
    val months = listOf(
        "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
        "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr",
    )

    /** Monday first, matching [SampleData.weekDays]. */
    val weekdays = listOf(
        "dushanba", "seshanba", "chorshanba", "payshanba", "juma", "shanba", "yakshanba",
    )

    /** "4-sentabr" */
    fun dayMonth(date: LocalDate): String = "${date.day}-${months[date.month.ordinal].lowercase()}"

    /** "4-sentabr, payshanba" */
    fun dayMonthWeekday(date: LocalDate): String =
        "${dayMonth(date)}, ${weekdays[date.dayOfWeek.ordinal]}"

    /** "Sentabr 2026" */
    fun monthYear(year: Int, month: Int): String = "${months[month - 1]} $year"

    /** "08:35" */
    fun time(dateTime: LocalDateTime): String =
        "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"

    /**
     * "hozir", "20 daqiqa oldin", "3 soat oldin", "kecha", "5 kun oldin" — how old a post
     * is, the way the feed reads it. Anything older than a month says the date.
     */
    fun ago(at: Instant, now: Instant): String {
        val seconds = (now - at).inWholeSeconds
        return when {
            seconds < 60 -> "hozir"
            seconds < 3600 -> "${seconds / 60} daqiqa oldin"
            seconds < 86_400 -> "${seconds / 3600} soat oldin"
            seconds < 2 * 86_400 -> "kecha"
            seconds < 30 * 86_400 -> "${seconds / 86_400} kun oldin"
            else -> dayMonth(at.toLocalDateTime(TimeZone.currentSystemDefault()).date)
        }
    }
}

/** The device's wall clock, in its own zone. */
fun deviceNow(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** "08:35" for right now — what a logged meal or a chat message is stamped with. */
fun nowTimeLabel(): String = Fmt.time(deviceNow())

/**
 * Which meal a log made at this hour belongs to. The scanner and the search both ask,
 * so the answer lives in one place.
 */
fun mealSlotForHour(hour: Int): String = when (hour) {
    in 4..10 -> "Nonushta"
    in 11..15 -> "Tushlik"
    in 16..21 -> "Kechki ovqat"
    else -> "Gazak"
}
