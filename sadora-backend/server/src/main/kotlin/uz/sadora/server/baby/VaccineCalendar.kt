package uz.sadora.server.baby

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.sadora.contract.Language

/**
 * Uzbekistan's national immunisation calendar for children, as doses dated from birth.
 *
 * The doses are data, in `baby/uz_vaccines.json`, with the official sources they were
 * taken from listed in the same file: a calendar changes by ministerial order, and the
 * next change should be an edit of that file and nothing else.
 */
object VaccineCalendar {

    @Serializable
    data class Words(val uz: String, val ru: String, val en: String) {
        fun of(language: Language) = when (language) {
            Language.UZ -> uz
            Language.RU -> ru
            Language.EN -> en
        }
    }

    @Serializable
    data class Dose(
        val code: String,
        val vaccine: String,
        val dose: Int? = null,
        val ageDays: Int? = null,
        val ageMonths: Int? = null,
        val ageYears: Int? = null,
        val names: Words,
        val note: Words? = null,
    ) {
        fun dueOn(birth: LocalDate): LocalDate = when {
            ageYears != null -> birth.plus(ageYears, DateTimeUnit.YEAR)
            ageMonths != null -> birth.plus(ageMonths, DateTimeUnit.MONTH)
            else -> birth.plus(ageDays ?: 0, DateTimeUnit.DAY)
        }

        fun ageLabel(language: Language): String = when {
            ageYears != null -> when (language) {
                Language.UZ -> "$ageYears yosh"
                Language.RU -> "$ageYears ${if (ageYears in 2..4) "года" else "лет"}"
                Language.EN -> "$ageYears years"
            }
            ageMonths != null -> when (language) {
                Language.UZ -> "$ageMonths oylik"
                Language.RU -> "$ageMonths мес."
                Language.EN -> "$ageMonths ${if (ageMonths == 1) "month" else "months"}"
            }
            ageDays == null || ageDays == 0 -> when (language) {
                Language.UZ -> "Tug'ilganda"
                Language.RU -> "При рождении"
                Language.EN -> "At birth"
            }
            else -> when (language) {
                Language.UZ -> "$ageDays kunlik"
                Language.RU -> "$ageDays дн."
                Language.EN -> "$ageDays days"
            }
        }
    }

    @Serializable
    private data class File(val doses: List<Dose>, val sources: List<Source> = emptyList())

    @Serializable
    data class Source(val url: String, val note: String? = null)

    private val file: File by lazy {
        val text = VaccineCalendar::class.java.getResourceAsStream("/baby/uz_vaccines.json")!!.bufferedReader().readText()
        Json { ignoreUnknownKeys = true }.decodeFromString<File>(text)
    }

    val doses: List<Dose> get() = file.doses

    private val byCode by lazy { doses.associateBy { it.code } }

    fun byCode(code: String): Dose? = byCode[code]
}
