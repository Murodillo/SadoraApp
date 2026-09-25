package uz.sadora.app.data

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.BillingCatalogue
import uz.sadora.contract.BillingPeriod
import uz.sadora.contract.BillingPlan
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.SubscriptionStatus
import uz.sadora.contract.SubscriptionTier

/**
 * A store build: the store sells, the server decides. A purchase is finished with the
 * store only after the server accepted its receipt, and one the server could not see
 * stays unfinished for the next launch.
 */
class StoreBillingTest {

    private class FakeStore(
        var outcome: StoreOutcome = StoreOutcome.Cancelled,
        var held: List<StoreReceipt> = emptyList(),
    ) : StoreBilling {
        override val provider = PaymentProvider.GOOGLE_PLAY
        val finished = mutableListOf<String>()
        var accountSeen: String? = null
        override suspend fun prices(productIds: List<String>) = productIds.associateWith { "39 000 so'm" }
        override suspend fun purchase(productId: String, accountId: String): StoreOutcome {
            accountSeen = accountId
            return outcome
        }
        override suspend fun owned() = held
        override suspend fun finish(receipt: StoreReceipt) { finished += receipt.token }
    }

    private val plan = BillingPlan(
        id = "premium_month",
        title = "Oylik",
        period = BillingPeriod.MONTH,
        priceMinor = 3_990_000,
        googlePlayProductId = "premium_month",
    )

    private fun controller(recording: RecordingEngine, store: FakeStore) =
        BillingController(
            SadoraGraph(
                tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
                device = FixedDeviceIdentity(),
                environment = SadoraEnvironment("http://test.local"),
                engine = recording.build(),
            ).billingApi,
            store,
        )

    private val serverAccepts = RecordingEngine { request ->
        when (request.url.encodedPath) {
            "/v1/billing/store/verify" -> json(encode(SubscriptionStatus(tier = SubscriptionTier.PREMIUM)))
            "/v1/billing/plans" -> json(encode(BillingCatalogue(listOf(plan), emptyList())))
            else -> json("{}")
        }
    }

    @Test
    fun `a purchase is verified by the server and then finished with the store`() = runTest {
        val store = FakeStore(outcome = StoreOutcome.Purchased(StoreReceipt("premium_month", "tok-1", needsFinish = true)))
        val billing = controller(serverAccepts, store)
        var refreshed = false

        billing.buyInStore(plan, accountId = "user-1") { refreshed = true }

        assertEquals("user-1", store.accountSeen)
        assertEquals(1, serverAccepts.countOf("/v1/billing/store/verify"))
        assertEquals(listOf("tok-1"), store.finished)
        assertTrue(billing.paid)
        assertTrue(refreshed)
    }

    @Test
    fun `a receipt the server refuses is not finished with the store`() = runTest {
        val refusing = RecordingEngine {
            json(errorBody(ErrorCodes.VALIDATION_FAILED, "Chek tasdiqlanmadi"), HttpStatusCode.BadRequest)
        }
        val store = FakeStore(outcome = StoreOutcome.Purchased(StoreReceipt("premium_month", "tok-2", needsFinish = true)))
        val billing = controller(refusing, store)

        billing.buyInStore(plan, accountId = "user-1") {}

        assertTrue(store.finished.isEmpty(), "an unverified purchase must stay open so it can be retried or refunded")
        assertFalse(billing.paid)
    }

    @Test
    fun `a pending purchase says so and grants nothing yet`() = runTest {
        val store = FakeStore(outcome = StoreOutcome.Pending)
        val billing = controller(serverAccepts, store)

        billing.buyInStore(plan, accountId = "user-1") {}

        assertTrue(billing.storePending)
        assertEquals(0, serverAccepts.countOf("/v1/billing/store/verify"))
    }

    @Test
    fun `reconcile sends what the store holds and finishes only what needs it`() = runTest {
        val store = FakeStore(
            held = listOf(
                StoreReceipt("premium_month", "left-open", needsFinish = true),
                StoreReceipt("premium_month", "already-done", needsFinish = false),
            ),
        )
        val billing = controller(serverAccepts, store)

        assertTrue(billing.reconcileStore())
        assertEquals(2, serverAccepts.countOf("/v1/billing/store/verify"))
        assertEquals(listOf("left-open"), store.finished)
    }

    @Test
    fun `a store build lists the store's prices`() = runTest {
        val billing = controller(serverAccepts, FakeStore())
        billing.loadCatalogue()
        billing.loadStorePrices()
        assertEquals(mapOf("premium_month" to "39 000 so'm"), billing.storePrices)
    }
}
