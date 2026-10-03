package uz.sadora.doctor.data

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.DoctorEarnings
import uz.sadora.contract.DoctorPayoutView
import uz.sadora.contract.EarningLine
import uz.sadora.contract.Page

/**
 * Her money and her work list arrive a page at a time: the earnings page reads on by
 * offset under the totals, and the questions list reads on by the rows the server has
 * given — less the ones she answered, so an answer never makes the next page skip one.
 */
class WorkPagingTest {

    private fun line(n: Int) = EarningLine(
        sessionId = "s$n",
        patientName = "Bemor $n",
        openedAt = TestNow - n.minutes,
        priceMinor = 5_000_000,
        commissionMinor = 750_000,
        netMinor = 4_250_000,
        payment = ConsultationPayment.PAID,
    )

    private fun earnings(lines: List<EarningLine>, linesTotal: Long, payoutsTotal: Long = 1) = DoctorEarnings(
        grossMinor = 5_000_000L * linesTotal,
        lines = lines,
        payouts = listOf(DoctorPayoutView("p1", 1_000_000, paidAt = TestNow)),
        linesTotal = linesTotal,
        payoutsTotal = payoutsTotal,
    )

    @Test
    fun `the earnings page reads on under the totals and drops a line it already has`() = runTest {
        val offsets = mutableListOf<String?>()
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/doctor/earnings" -> json(encode(earnings((1..50).map(::line), linesTotal = 52)))
                "/v1/doctor/earnings/lines" -> {
                    offsets.add(request.url.parameters["offset"])
                    // A consultation paid in between: line 50 comes again, and one more than before.
                    json(encode(Page(listOf(line(50), line(51), line(52)), total = 53, limit = 50, offset = 50)))
                }
                else -> json(errorBody("not_found"), HttpStatusCode.NotFound)
            }
        }
        val work = testGraph(recording).workController()

        work.loadEarnings()
        assertEquals(50, work.earnings?.lines?.size)
        assertTrue(work.linesHaveMore)
        assertFalse(work.payoutsHaveMore, "one payout, and it is here")

        work.loadMoreLines()
        assertEquals(listOf<String?>("50"), offsets)
        assertEquals((1..52).map { "s$it" }, work.earnings?.lines?.map { it.sessionId })
        assertFalse(work.linesHaveMore)
        assertEquals(5_000_000L * 52, work.earnings?.grossMinor, "the totals stay the server's")

        work.loadMorePayouts()
        assertEquals(0, recording.countOf("/v1/doctor/earnings/payouts"), "nothing more to ask for")

        // A fresh read starts from the top again, with its own first page.
        work.loadEarnings(silent = true)
        assertEquals(50, work.earnings?.lines?.size)
        assertTrue(work.linesHaveMore)
    }

    @Test
    fun `a server that sent every line at once is not asked for more`() = runTest {
        // The shape before paging: no totals, so nothing says there is more.
        val recording = RecordingEngine { json(encode(DoctorEarnings(lines = (1..3).map(::line)))) }
        val work = testGraph(recording).workController()

        work.loadEarnings()
        work.loadMoreLines()

        assertEquals(3, work.earnings?.lines?.size)
        assertFalse(work.linesHaveMore)
        assertEquals(0, recording.countOf("/v1/doctor/earnings/lines"))
    }

    private fun question(n: Int): CommunityPost = testQuestion("q$n").copy(createdAt = TestNow - n.minutes)

    @Test
    fun `the questions read on by what the server gave — less what she answered`() = runTest {
        var waiting = (0 until 120).map(::question)
        val offsets = mutableListOf<String?>()
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/v1/doctor/questions" -> {
                    offsets.add(request.url.parameters["offset"])
                    val offset = request.url.parameters["offset"]?.toInt() ?: 0
                    val limit = request.url.parameters["limit"]!!.toInt()
                    json(encode(waiting.drop(offset).take(limit)))
                }
                path == "/v1/community/posts/q3/comments" && request.method == HttpMethod.Post -> {
                    // Answered, it leaves the server's list: every row after it moves up one.
                    waiting = waiting.filterNot { it.id == "q3" }
                    json(encode(testComment("c1", "q3", doctor = TestDoctor)), HttpStatusCode.Created)
                }
                else -> json(errorBody("not_found"), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()

        doctors.loadQuestions()
        assertEquals(DoctorApi.QUESTION_PAGE, doctors.questions.size)
        assertTrue(doctors.questionsHasMore)

        assertTrue(doctors.answer("q3", "Javob"))
        assertEquals(49, doctors.questionsOffset)

        // Read on from 49, not 50: the row that took q3's place is q50, and it is not skipped.
        doctors.loadMoreQuestions()
        assertEquals(listOf<String?>("0", "49"), offsets)
        assertEquals((0 until 100).filter { it != 3 }.map { "q$it" }, doctors.questions.map { it.id })
        assertTrue(doctors.questionsHasMore)

        // A question asked meanwhile pushes every row down one: the repeat is dropped by id.
        waiting = listOf(testQuestion("fresh").copy(createdAt = TestNow + 1.minutes)) + waiting
        doctors.loadMoreQuestions()
        assertEquals("99", offsets.last())
        assertEquals((0 until 120).filter { it != 3 }.map { "q$it" }, doctors.questions.map { it.id })
        assertFalse(doctors.questionsHasMore, "a short page is the last")

        // Home's refresh brings the newest page and keeps the older ones under it.
        doctors.loadQuestions()
        assertEquals("fresh", doctors.questions.first().id)
        assertEquals(120, doctors.questions.size)
    }
}
