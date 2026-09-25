package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.PaymentProvider

/** The Kotlin half of the StoreKit bridge: Swift's flat callbacks become the shared outcomes. */
class IosStoreBillingTest {

    private class FakeBridge(private val outcome: StoreKitResult) : StoreKitBridge {
        var token: String? = null
        val finished = mutableListOf<String>()
        override fun prices(productIds: List<String>, done: (Map<String, String>) -> Unit) =
            done(productIds.associateWith { "\$2.99" })
        override fun purchase(productId: String, accountToken: String, done: (StoreKitResult) -> Unit) {
            token = accountToken
            done(outcome)
        }
        override fun owned(done: (List<StoreKitResult>) -> Unit) = done(
            listOf(
                StoreKitResult("purchased", "uz.sadora.premium.month", "jws-1", null),
                StoreKitResult("purchased", null, "no-product", null),
            ),
        )
        override fun finish(jws: String, done: () -> Unit) {
            finished += jws
            done()
        }
    }

    @Test
    fun aPurchaseCarriesTheJwsAndTheAccount() = runTest {
        val bridge = FakeBridge(StoreKitResult("purchased", "uz.sadora.premium.month", "jws-9", null))
        val store = IosStoreBilling(bridge)

        val outcome = assertIs<StoreOutcome.Purchased>(store.purchase("uz.sadora.premium.month", "user-7"))

        assertEquals(PaymentProvider.APP_STORE, store.provider)
        assertEquals("jws-9", outcome.receipt.token)
        assertEquals("user-7", bridge.token)
        store.finish(outcome.receipt)
        assertEquals(listOf("jws-9"), bridge.finished)
    }

    @Test
    fun cancelledPendingAndFailedMapAcross() = runTest {
        assertIs<StoreOutcome.Cancelled>(IosStoreBilling(FakeBridge(StoreKitResult("cancelled", null, null, null))).purchase("p", "u"))
        assertIs<StoreOutcome.Pending>(IosStoreBilling(FakeBridge(StoreKitResult("pending", null, null, null))).purchase("p", "u"))
        assertIs<StoreOutcome.Failed>(IosStoreBilling(FakeBridge(StoreKitResult("failed", null, null, "no"))).purchase("p", "u"))
        // "purchased" without a JWS is not a purchase anyone can verify.
        assertIs<StoreOutcome.Failed>(IosStoreBilling(FakeBridge(StoreKitResult("purchased", "p", null, null))).purchase("p", "u"))
    }

    @Test
    fun ownedKeepsOnlyWhatCanBeVerified() = runTest {
        val owned = IosStoreBilling(FakeBridge(StoreKitResult("cancelled", null, null, null))).owned()
        assertEquals(listOf("jws-1"), owned.map { it.token })
        assertEquals(mapOf("a" to "\$2.99"), IosStoreBilling(FakeBridge(StoreKitResult("x", null, null, null))).prices(listOf("a")))
    }
}
