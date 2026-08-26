package org.example.project.model

/**
 * Number formatting for the Uzbek locale used throughout the design:
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
}
