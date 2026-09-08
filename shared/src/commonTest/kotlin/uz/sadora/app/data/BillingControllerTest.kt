package uz.sadora.app.data

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.BillingCatalogue
import uz.sadora.contract.BillingPeriod
import uz.sadora.contract.BillingPlan
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.contract.PaymentStatus

/**
 * The paywall's rules on the app side: prices come from the server, a checkout link is
 * asked for rather than assembled, and Premium is only ever believed when the server
 * says the payment landed.
 */
class BillingControllerTest {

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private val catalogue = BillingCatalogue(
        plans = listOf(
            BillingPlan(
                id = "premium_year",
                title = "Yillik",
                period = BillingPeriod.YEAR,
                priceMinor = 29_900_000,
                monthlyEquivalentMinor = 2_491_666,
                highlighted = true,
            ),
        ),
        providers = listOf(PaymentProvider.PAYME),
    )

    private val session = CheckoutSession(
        transactionId = "tx-1",
        provider = PaymentProvider.PAYME,
        url = "https://checkout.paycom.uz/abc",
        amountMinor = 29_900_000,
    )

    private fun status(state: PaymentState) = PaymentStatus(
        transactionId = "tx-1",
        state = state,
        provider = PaymentProvider.PAYME,
        planId = "premium_year",
        amountMinor = 29_900_000,
    )

    @Test
    fun `the catalogue is fetched once and kept`() = runTest {
        val recording = RecordingEngine { json(encode(catalogue)) }
        val billing = graph(recording).billingController()

        billing.loadCatalogue()
        billing.loadCatalogue()

        assertEquals(1, recording.paths.count { it == "/v1/billing/plans" })
        assertEquals(29_900_000, billing.catalogue?.plans?.first()?.priceMinor)
    }

    @Test
    fun `checkout hands back the provider's link and remembers it is waiting`() = runTest {
        val recording = RecordingEngine { json(encode(session)) }
        val billing = graph(recording).billingController()

        val started = billing.startCheckout("premium_year", PaymentProvider.PAYME)

        assertEquals("https://checkout.paycom.uz/abc", assertNotNull(started).url)
        assertNotNull(billing.pending)
        assertNull(billing.error)
    }

    @Test
    fun `a refused checkout is reported and leaves nothing pending`() = runTest {
        val recording = RecordingEngine {
            json(errorBody(ErrorCodes.FEATURE_DISABLED, "Bu bo'lim hozircha yopiq"), HttpStatusCode.Forbidden)
        }
        val billing = graph(recording).billingController()

        assertNull(billing.startCheckout("premium_year", PaymentProvider.PAYME))
        assertNull(billing.pending)
        assertNotNull(billing.error)
    }

    @Test
    fun `waiting ends when the server says the payment landed and only then`() = runTest {
        var polls = 0
        val recording = RecordingEngine { request ->
            if (request.url.encodedPath.endsWith("/checkout")) {
                json(encode(session))
            } else {
                polls++
                json(encode(status(if (polls >= 2) PaymentState.PAID else PaymentState.PENDING)))
            }
        }
        val billing = graph(recording).billingController()
        billing.startCheckout("premium_year", PaymentProvider.PAYME)

        var refreshed = false
        billing.awaitPayment { refreshed = true }

        assertTrue(billing.paid)
        assertTrue(refreshed, "the entitlements are re-read from the server, not assumed")
        assertNull(billing.pending)
        assertEquals(2, polls, "it stops asking as soon as it has an answer")
    }

    @Test
    fun `a cancelled payment says so and grants nothing`() = runTest {
        val recording = RecordingEngine { request ->
            if (request.url.encodedPath.endsWith("/checkout")) {
                json(encode(session))
            } else {
                json(encode(status(PaymentState.CANCELLED)))
            }
        }
        val billing = graph(recording).billingController()
        billing.startCheckout("premium_year", PaymentProvider.PAYME)

        var refreshed = false
        billing.awaitPayment { refreshed = true }

        assertFalse(billing.paid)
        assertFalse(refreshed)
        assertNotNull(billing.error)
    }

    @Test
    fun `with no backend there are no prices and so no offer`() = runTest {
        val billing = BillingController(null)
        billing.loadCatalogue()

        assertNull(billing.catalogue)
        assertNull(billing.startCheckout("premium_year", PaymentProvider.PAYME))
        assertTrue(billing.isOffline)
    }
}
