package uz.sadora.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import uz.sadora.contract.LabFlag
import uz.sadora.contract.LabPhotoUpload
import uz.sadora.contract.LabRanges
import uz.sadora.contract.LabReport
import uz.sadora.contract.LabValue
import uz.sadora.contract.Language
import uz.sadora.server.ai.GeminiLabVision
import uz.sadora.server.ai.LabVisionRow
import uz.sadora.server.ai.LabVisionSheet
import uz.sadora.server.ai.ModelUnavailableException
import uz.sadora.server.config.AiConfig
import uz.sadora.server.lab.LabMarkers
import uz.sadora.server.lab.LabScanCleaner
import uz.sadora.server.lab.LabWords

/**
 * The parts of lab results that need no database: which printed name is which marker,
 * what a printed range makes of a number, what the model's copy of a sheet is cleaned
 * into, and the text a chat line carries.
 */
class LabResultsTest {

    // ---------------------------------------------------------------- markers

    @Test
    fun `printed names are matched the way sheets here print them`() {
        assertEquals("hgb", LabMarkers.match("Гемоглобин (HGB)")?.code)
        assertEquals("hgb", LabMarkers.match("HGB")?.code)
        assertEquals("tsh", LabMarkers.match("ТТГ (тиреотропный гормон)")?.code)
        assertEquals("vitd", LabMarkers.match("25(OH)D")?.code)
        assertEquals("ft4", LabMarkers.match("Т4 свободный")?.code)
        assertEquals("esr", LabMarkers.match("СОЭ")?.code)
        assertEquals("ferritin", LabMarkers.match("  ферритин ")?.code)
        assertNull(LabMarkers.match("Билирубин прямой"), "an unknown row stays as printed")
    }

    @Test
    fun `a code the model gives wins only when the catalogue knows it`() {
        assertEquals("ferritin", LabMarkers.resolve("ferritin", "whatever was printed")?.code)
        assertEquals("hgb", LabMarkers.resolve("made_up", "Гемоглобин")?.code)
        assertNull(LabMarkers.resolve("made_up", "Something"))
    }

    @Test
    fun `every marker speaks all three languages and has no duplicate alias`() {
        LabMarkers.all.forEach { marker ->
            listOf(marker.names.uz, marker.names.ru, marker.names.en, marker.about.uz, marker.about.ru, marker.about.en)
                .forEach { assertTrue(it.isNotBlank(), marker.code) }
        }
        val aliases = LabMarkers.all.flatMap { m -> m.aliases.map { LabMarkers.normalise(it) to m.code } }
        val clashes = aliases.groupBy({ it.first }, { it.second }).filterValues { it.distinct().size > 1 }
        assertTrue(clashes.isEmpty(), "aliases shared by two markers: $clashes")
        assertEquals(LabMarkers.all.size, LabMarkers.all.map { it.code }.distinct().size)
    }

    // ---------------------------------------------------------------- flags

    @Test
    fun `the printed range decides, and no range is no flag`() {
        assertEquals(LabFlag.LOW, LabRanges.flagOf(104.0, 120.0, 150.0))
        assertEquals(LabFlag.NORMAL, LabRanges.flagOf(120.0, 120.0, 150.0), "the bound itself is in range")
        assertEquals(LabFlag.HIGH, LabRanges.flagOf(5.1, null, 5.0))
        assertEquals(LabFlag.NORMAL, LabRanges.flagOf(31.0, 30.0, null))
        assertNull(LabRanges.flagOf(4.0, null, null))
        assertNull(LabRanges.flagOf(null, 1.0, 2.0))
        assertNull(LabRanges.flagOf(4.0, 5.0, 2.0), "a reversed range is not trusted")
    }

    // ---------------------------------------------------------------- the scanner's answer

    @Test
    fun `what the model wrote is cleaned before she sees it`() {
        val today = LocalDate(2026, 10, 18)
        val cleaned = LabScanCleaner.clean(
            LabVisionSheet(
                isLabReport = true,
                takenOn = "2027-01-01",
                place = "  Invitro  ",
                values = listOf(
                    LabVisionRow(code = "hgb", name = "Гемоглобин", value = 104.0, unit = "г/л", refLow = 120.0, refHigh = 150.0),
                    LabVisionRow(code = "nonsense", name = "Ферритин", value = 9.0, refLow = 50.0, refHigh = 10.0),
                    LabVisionRow(name = "ВИЧ", valueText = "отрицательно"),
                    LabVisionRow(name = "Empty row"),
                    LabVisionRow(name = "", value = 3.0),
                ),
            ),
            today,
        )
        assertTrue(cleaned.isLabReport)
        assertNull(cleaned.takenOn, "a date in the future is a misread, not a proposal")
        assertEquals("Invitro", cleaned.place)
        assertEquals(listOf("Гемоглобин", "Ферритин", "ВИЧ"), cleaned.values.map { it.name })
        assertEquals(listOf("hgb", "ferritin", null), cleaned.values.map { it.markerCode })
        assertNull(cleaned.values[1].refLow, "a reversed range is dropped")
        assertNull(cleaned.values[1].refHigh)
        assertEquals("отрицательно", cleaned.values[2].valueText)
    }

    @Test
    fun `a sheet with no rows is not a sheet`() {
        val cleaned = LabScanCleaner.clean(
            LabVisionSheet(isLabReport = true, takenOn = "2026-10-01", values = emptyList(), message = "Rasm xira"),
            LocalDate(2026, 10, 18),
        )
        assertFalse(cleaned.isLabReport)
        assertNull(cleaned.takenOn)
        assertEquals("Rasm xira", cleaned.message)
    }

    // ---------------------------------------------------------------- Gemini

    private val config = AiConfig(
        apiKey = "test-key",
        model = "gemini-3.6-flash",
        endpoint = "https://models.test",
        timeout = 5.seconds,
        maxOutputTokens = 800,
        inputCostPerMillionMicros = 100_000,
        outputCostPerMillionMicros = 400_000,
    )

    private var lastRequest: HttpRequestData? = null

    private fun vision(text: String): GeminiLabVision {
        val reply = buildJsonObject {
            putJsonArray("candidates") {
                addJsonObject {
                    put("content", buildJsonObject { putJsonArray("parts") { addJsonObject { put("text", JsonPrimitive(text)) } } })
                }
            }
            put("usageMetadata", buildJsonObject { put("promptTokenCount", 1200); put("candidatesTokenCount", 300) })
        }.toString()
        val engine = MockEngine { request ->
            lastRequest = request
            respond(reply, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        return GeminiLabVision(client, config)
    }

    @Test
    fun `every page goes in one call and the codes are offered`() = runTest {
        val answer = vision(
            """{"isLabReport":true,"takenOn":"2026-10-01","place":"Invitro","values":[{"code":"hgb","name":"Гемоглобин","value":104,"unit":"г/л","refLow":120,"refHigh":150}]}""",
        ).read(
            listOf(LabPhotoUpload("AAAA", "image/jpeg"), LabPhotoUpload("BBBB", "image/png")),
            Language.RU,
            listOf("hgb", "tsh"),
        )
        assertEquals(104.0, answer.sheet.values.single().value)
        assertEquals(1200, answer.promptTokens)
        val sent = (lastRequest!!.body as TextContent).text
        assertTrue("\"AAAA\"" in sent && "\"BBBB\"" in sent, "both pages")
        assertTrue("hgb, tsh" in sent, "the codes are in the instruction")
        assertTrue("Russian" in sent)
    }

    @Test
    fun `prose instead of the object is a failure, not an empty sheet`() = runTest {
        assertFailsWith<ModelUnavailableException> {
            vision("Here are the results: haemoglobin is low.").read(listOf(LabPhotoUpload("AAAA")), Language.UZ, emptyList())
        }
    }

    // ---------------------------------------------------------------- the chat line

    @Test
    fun `the chat line reads as the results and stays inside a message`() {
        val now = Clock.System.now()
        val report = LabReport(
            id = "r",
            takenOn = LocalDate(2026, 10, 1),
            place = "Invitro",
            values = listOf(
                LabValue("1", "hgb", "Gemoglobin", value = 104.0, unit = "g/L", refLow = 120.0, refHigh = 150.0, flag = LabFlag.LOW),
                LabValue("2", null, "ВИЧ", valueText = "manfiy"),
            ),
            createdAt = now,
            updatedAt = now,
        )
        val text = LabWords.plainText(report, Language.UZ)
        assertEquals("🧪 Tahlil natijalari · 01.10.2026 · Invitro\n• Gemoglobin: 104 g/L (past; norma 120–150)\n• ВИЧ: manfiy", text)

        val long = report.copy(values = List(60) { LabValue("$it", null, "Ko'rsatkich raqami $it", value = it * 1.5, unit = "mmol/L", refLow = 1.0, refHigh = 2.0) })
        val cut = LabWords.plainText(long, Language.UZ)
        assertTrue(cut.length <= 1000, "${cut.length}")
        assertTrue(Regex("… \\+\\d+$").containsMatchIn(cut), "says how many rows did not fit")
    }
}
