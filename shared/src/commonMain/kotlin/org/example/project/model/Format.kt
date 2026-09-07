package org.example.project.model

import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.MealSlot

/**
 * Numbers and clock times — the parts of formatting that do not change with the
 * language: thousands separated by a space ("1 240"), decimals with a comma ("1,2").
 *
 * Anything made of words — month names, weekdays, "3 soat oldin" — lives in
 * [org.example.project.i18n.DateStrings] instead, because this object is one instance
 * for the whole process while the language belongs to the screen doing the asking.
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

    /** "08:35", from a wall-clock time that carries no date. */
    fun clock(at: LocalTime): String =
        "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"

    /** "08:35" */
    fun time(dateTime: LocalDateTime): String =
        "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
}

/** The device's wall clock, in its own zone. */
fun deviceNow(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** "08:35" for right now — what a logged meal or a chat message is stamped with. */
fun nowTimeLabel(): String = Fmt.time(deviceNow())

/**
 * Which meal a log made at this hour belongs to. The scanner and the search both ask,
 * so the answer lives in one place.
 *
 * It answers with the slot itself rather than its name: the name belongs to whichever
 * language the screen is in, and the slot is what goes up to the server.
 */
fun mealSlotForHour(hour: Int): MealSlot = when (hour) {
    in 4..10 -> MealSlot.BREAKFAST
    in 11..15 -> MealSlot.LUNCH
    in 16..21 -> MealSlot.DINNER
    else -> MealSlot.SNACK
}
