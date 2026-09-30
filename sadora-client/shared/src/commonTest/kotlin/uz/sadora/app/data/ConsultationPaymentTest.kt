package uz.sadora.app.data

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest
import uz.sadora.app.model.AppState
import uz.sadora.app.nav.AppLink
import uz.sadora.contract.Ack
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.Consultation
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.DoctorAuthor
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.contract.PaymentStatus

/**
 * Paid consultations as the patient's app drives them: a 402 from the start call is a
 * pay sheet and not a banner, a consultation's payment is believed only when the server
 * says it landed, a rating given twice is still a rating, and a tapped push names the
 * thread it opens.
 */
class ConsultationPaymentTest {

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private val session = CheckoutSession(
        transactionId = "tx-9",
        provider = PaymentProvider.CLICK,
        url = "http://test.local/v1/billing/dev-pay/tx-9",
        amountMinor = 5_000_000,
    )

    private fun status(state: PaymentState) = PaymentStatus(
        transactionId = "tx-9",
        state = state,
        provider = PaymentProvider.CLICK,
        amountMinor = 5_000_000,
        consultationSessionId = "s-1",
    )

    private fun consultationThread(consultation: Consultation) = ConversationThread(
        conversation = Conversation(
            id = "c1",
            alias = "Dr. Nodira Karimova",
            tint = 0,
            lastMessageAt = TestNow,
            doctor = DoctorAuthor("d1", "Dr. Nodira Karimova", DoctorSpecialty.GYNECOLOGIST),
            consultation = consultation,
        ),
        messages = emptyList(),
    )

    @Test
    fun `a paid doctor's 402 is read as a payment to make and cleared for the sheet`() = runTest {
        val recording = RecordingEngine {
            json(
                errorBody(ErrorCodes.CONSULTATION_PAYMENT_REQUIRED, "Konsultatsiya pullik", mapOf("priceMinor" to "5000000")),
                HttpStatusCode.PaymentRequired,
            )
        }
        val messages = graph(recording).messagesController()

        assertNull(messages.startConsultation("d1"))
        val failure = assertIs<ApiFailure.PaymentRequired>(messages.error)
        assertEquals(5_000_000, failure.priceMinor)

        assertTrue(messages.takePaymentRequired())
        assertNull(messages.error, "the pay sheet answers it; no banner stays behind")
        assertFalse(messages.takePaymentRequired())
    }

    @Test
    fun `the consultation checkout posts the provider and waits until the server says paid`() = runTest {
        var polls = 0
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/v1/doctors/d1/consultations/checkout" && request.method == HttpMethod.Post ->
                    json(encode(session), HttpStatusCode.Created)
                path == "/v1/billing/payments/tx-9" -> {
                    polls++
                    json(encode(status(if (polls >= 3) PaymentState.PAID else PaymentState.PENDING)))
                }
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }
        val doctors = graph(recording).doctorController(AppState())

        val started = assertNotNull(doctors.startCheckout("d1", PaymentProvider.CLICK))
        assertEquals("http://test.local/v1/billing/dev-pay/tx-9", started.url)
        assertTrue(recording.bodies.any { it.contains("\"provider\":\"click\"") }, recording.bodies.toString())
        assertEquals("d1", doctors.checkoutFor)

        assertTrue(doctors.awaitPayment())
        assertEquals(3, polls, "it stops asking as soon as it has an answer")
        assertNull(doctors.checkout)
        assertNull(doctors.payError)
    }

    @Test
    fun `a cancelled consultation payment ends the wait with an error`() = runTest {
        val recording = RecordingEngine { request ->
            if (request.url.encodedPath.endsWith("/checkout")) {
                json(encode(session), HttpStatusCode.Created)
            } else {
                json(encode(status(PaymentState.CANCELLED)))
            }
        }
        val doctors = graph(recording).doctorController(AppState())
        doctors.startCheckout("d1", PaymentProvider.PAYME)

        assertFalse(doctors.awaitPayment())
        assertNull(doctors.checkout)
        assertEquals(ApiFailure.PaymentFailed, doctors.payError)
    }

    @Test
    fun `a rating already given counts as given and the thread is re-read`() = runTest {
        val window = Consultation(
            openedAt = TestNow - 30.hours,
            expiresAt = TestNow - 6.hours,
            open = false,
            priceMinor = 5_000_000,
            payment = ConsultationPayment.PAID,
            summary = "Kuniga 2 litr suv iching.",
            canRate = true,
            answered = true,
        )
        var rated = false
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/conversations/c1/rating") -> {
                    rated = true
                    json(errorBody(ErrorCodes.CONFLICT, "Allaqachon baholangansiz"), HttpStatusCode.Conflict)
                }
                path.endsWith("/conversations/c1") ->
                    json(encode(consultationThread(if (rated) window.copy(canRate = false, rating = 5) else window)))
                else -> json(encode(Ack()))
            }
        }
        val messages = graph(recording).messagesController()
        messages.open("c1", "Dr. Nodira Karimova")

        val before = assertNotNull(messages.current?.consultation)
        assertTrue(before.canRate)
        assertEquals(ConsultationPayment.PAID, before.payment)
        assertEquals("Kuniga 2 litr suv iching.", before.summary)

        assertTrue(messages.rate(5, "  Rahmat  "))
        assertNull(messages.error, "a 409 here is not an error she needs to see")
        assertFalse(assertNotNull(messages.current?.consultation).canRate)
        assertTrue(recording.bodies.any { it.contains("\"rating\":5") && it.contains("\"review\":\"Rahmat\"") })
    }

    @Test
    fun `a push link names the conversation it opens`() {
        assertEquals(
            AppLink.Conversation("0f8e7c1a-3b2d-4c5e-9f00-112233445566"),
            AppLink.parse("sadora://conversation/0f8e7c1a-3b2d-4c5e-9f00-112233445566"),
        )
        assertNull(AppLink.parse("sadora://conversation/"))
        assertNull(AppLink.parse("sadora://conversation/a b"))
        assertNull(AppLink.parse("https://evil.example/conversation/0f8e7c1a-3b2d"), "only the app's own scheme")
    }
}
