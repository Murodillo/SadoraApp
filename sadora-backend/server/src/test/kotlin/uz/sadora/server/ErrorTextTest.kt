package uz.sadora.server

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uz.sadora.contract.Language
import uz.sadora.server.i18n.ErrorText
import uz.sadora.server.i18n.errorLanguageOf

class ErrorTextTest {

    @Test
    fun `a whole sentence is looked up as it is`() {
        assertEquals("Пост не найден", ErrorText.translate("Post topilmadi", Language.RU))
        assertEquals("Post not found", ErrorText.translate("Post topilmadi", Language.EN))
    }

    @Test
    fun `Uzbek and unknown sentences go out unchanged`() {
        assertEquals("Post topilmadi", ErrorText.translate("Post topilmadi", Language.UZ))
        assertEquals("Hech kim yozmagan gap", ErrorText.translate("Hech kim yozmagan gap", Language.RU))
    }

    @Test
    fun `the values in a template are carried across`() {
        assertEquals(
            "Нельзя написать больше 20 постов в день",
            ErrorText.translate("Bir kunda 20 tadan ko'p post yozib bo'lmaydi", Language.RU),
        )
        assertEquals("Must be between 80 and 250", ErrorText.translate("80–250 oralig'ida bo'lishi kerak", Language.EN))
        assertEquals(
            "Вход через Apple пока не работает. Войдите по номеру телефона.",
            ErrorText.translate("Apple orqali kirish hozircha ishlamayapti. Telefon raqam bilan kiring.", Language.RU),
        )
        assertEquals("Недостаточно Gul", ErrorText.translate("Gul yetarli emas", Language.RU))
    }

    @Test
    fun `a field-aware rule names the field in every language`() {
        assertEquals("Заполните поле «Название лекарства»", ErrorText.translate("Dori nomini yozing", Language.RU))
        assertEquals("Medication name is required", ErrorText.translate("Dori nomini yozing", Language.EN))
        assertEquals("Поле «Имя»: не больше 60 символов", ErrorText.translate("Ism eng ko'pi 60 belgi bo'lsin", Language.RU))
        assertEquals("Text: at least 3 characters", ErrorText.translate("Matn kamida 3 ta belgi bo'lsin", Language.EN))
        // An exact sentence that happens to fit the shape still wins.
        assertEquals("Please give a reason", ErrorText.translate("Sababini yozing", Language.EN))
    }

    @Test
    fun `a field nobody named is not translated and fails the source check`() {
        assertEquals("Pasport raqamini yozing", ErrorText.translate("Pasport raqamini yozing", Language.RU))
        assertTrue(!ErrorText.covers("Pasport raqamini yozing"))
        assertTrue(ErrorText.covers("Ism-familiya kamida 7 ta belgi bo'lsin"))
    }

    @Test
    fun `every field name is translated and listed once`() {
        val repeated = ErrorText.labels.groupBy { it.uz }.filterValues { it.size > 1 }.keys
        assertTrue(repeated.isEmpty(), "listed twice: $repeated")
        ErrorText.labels.forEach {
            assertTrue(it.ru.isNotBlank() && it.en.isNotBlank() && it.ru != it.uz, it.uz)
        }
    }

    @Test
    fun `no refusal tells her about the server's insides`() {
        val jargon = listOf("UUID", "YYYY", "endpoint", "token", "Token", "токен", "Store xarid", "гул", "({0})")
        ErrorText.entries.forEach { entry ->
            listOf(entry.uz, entry.ru, entry.en).forEach { text ->
                jargon.forEach { word -> assertTrue(word !in text, "\"$word\" in: $text") }
            }
        }
    }

    @Test
    fun `the most specific template wins over a shorter one that would also match`() {
        assertEquals("At most 500 characters", ErrorText.translate("Eng ko'pi 500 belgi", Language.EN))
        assertEquals("At most 30", ErrorText.translate("Eng ko'pi 30", Language.EN))
        assertEquals("At most 7 documents", ErrorText.translate("Eng ko'pi 7 ta hujjat", Language.EN))
    }

    @Test
    fun `every translation keeps the placeholders of its Uzbek`() {
        val placeholder = Regex("""\{\d}""")
        ErrorText.entries.forEach { entry ->
            val expected = placeholder.findAll(entry.uz).map { it.value }.toSortedSet()
            assertEquals(expected, placeholder.findAll(entry.ru).map { it.value }.toSortedSet(), entry.uz)
            assertEquals(expected, placeholder.findAll(entry.en).map { it.value }.toSortedSet(), entry.uz)
        }
    }

    @Test
    fun `no sentence is listed twice`() {
        val repeated = ErrorText.entries.groupBy { it.uz }.filterValues { it.size > 1 }.keys
        assertTrue(repeated.isEmpty(), "listed twice: $repeated")
    }

    @Test
    fun `the language is the first tag of Accept-Language`() {
        assertEquals(Language.RU, errorLanguageOf("ru"))
        assertEquals(Language.RU, errorLanguageOf("ru-RU,ru;q=0.9,en;q=0.8"))
        assertEquals(Language.EN, errorLanguageOf("en-US"))
        assertEquals(Language.UZ, errorLanguageOf("uz"))
        assertEquals(Language.UZ, errorLanguageOf("de"))
        assertEquals(Language.UZ, errorLanguageOf(null))
    }

    /**
     * Reads every refusal the API can send an app and checks the catalogue knows it.
     *
     * A new `throw ValidationException(...)` with a sentence nobody translated fails
     * here instead of reaching a Russian-speaking woman in Uzbek. The staff panel's own
     * package is left out: the panels ask for Uzbek.
     */
    @Test
    fun `every refusal in the sources has a translation`() {
        val root = File("src/main/kotlin/uz/sadora/server")
        assertTrue(root.isDirectory, "run from the server project: ${root.absolutePath}")
        val missing = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.relativeTo(root).path.startsWith("admin/") }
            .flatMap { file -> sentencesIn(file.readText()).map { file.relativeTo(root).path to it } }
            .filterNot { (_, sentence) -> ErrorText.covers(sentence) }
            .toList()
        assertTrue(missing.isEmpty(), "untranslated:\n" + missing.joinToString("\n") { (file, s) -> "$file: $s" })
    }

    private fun sentencesIn(source: String): List<String> {
        val found = mutableListOf<String>()
        ApiThrows.findAll(source).forEach { match ->
            found += literalsInCall(source, match.range.last)
        }
        MessageDefaults.findAll(source).forEach { match ->
            found += literalsAt(source, match.range.last, stopAfterOne = true)
        }
        return found.filter { it.isNotBlank() && !FieldName.matches(it) }
    }

    /** The literals between the parenthesis at [open] and its partner. */
    private fun literalsInCall(source: String, open: Int): List<String> {
        val pieces = mutableListOf<String>()
        var depth = 1
        var i = open + 1
        var previousEnd = -1
        while (i < source.length && depth > 0) {
            when (source[i]) {
                '"' -> {
                    val (text, end) = readLiteral(source, i)
                    val between = if (previousEnd >= 0) source.substring(previousEnd, i) else ""
                    if (previousEnd >= 0 && between.isNotBlank() && between.trim() == "+") {
                        pieces[pieces.lastIndex] = pieces.last() + text
                    } else {
                        pieces += text
                    }
                    previousEnd = end
                    i = end
                    continue
                }
                '(' -> depth++
                ')' -> depth--
            }
            i++
        }
        return pieces
    }

    private fun literalsAt(source: String, quote: Int, stopAfterOne: Boolean): List<String> =
        if (stopAfterOne) listOf(readLiteral(source, quote).first) else emptyList()

    /**
     * The literal opening at [quote], with each `$name` and `${…}` read as "7" — what a
     * number or a name looks like once the sentence is built. Returns the text and the
     * index just past the closing quote.
     */
    private fun readLiteral(source: String, quote: Int): Pair<String, Int> {
        val text = StringBuilder()
        var j = quote + 1
        while (source[j] != '"') {
            val c = source[j]
            when {
                c == '\\' -> {
                    text.append(source[j + 1])
                    j += 2
                }
                c == '$' && source[j + 1] == '{' -> {
                    var braces = 1
                    j += 2
                    while (braces > 0) {
                        when (source[j]) {
                            '{' -> braces++
                            '}' -> braces--
                            '"' -> {
                                j++
                                while (source[j] != '"') j += if (source[j] == '\\') 2 else 1
                            }
                        }
                        j++
                    }
                    text.append('7')
                }
                c == '$' && (source[j + 1].isLetter() || source[j + 1] == '_') -> {
                    j++
                    while (source[j].isLetterOrDigit() || source[j] == '_') j++
                    text.append('7')
                }
                else -> {
                    text.append(c)
                    j++
                }
            }
        }
        return text.toString() to j + 1
    }

    private companion object {
        val ApiThrows = Regex(
            """\b(?:ApiException|ValidationException|UnauthorizedException|ForbiddenException|NotFoundException|""" +
                """ConflictException|RateLimitedException|UpstreamUnavailableException|ProviderUnavailableException|""" +
                """ApiOtpException)\(""",
        )

        /** `message: String = "…"` defaults and `message = "…"` arguments outside a throw. */
        val MessageDefaults = Regex("""\bmessage(?::\s*String)?\s*=\s*"""")

        /** `"days"`, `"quietFrom"` — the field a reason belongs to, not a sentence. */
        val FieldName = Regex("""[a-z][a-zA-Z0-9_.]*""")
    }
}
