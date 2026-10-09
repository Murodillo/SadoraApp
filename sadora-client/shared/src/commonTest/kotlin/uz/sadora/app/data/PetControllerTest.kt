package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import uz.sadora.app.model.AppState
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.contract.PaymentStatus
import uz.sadora.contract.PetAction
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetNudge
import uz.sadora.contract.PetNudgeAnswer
import uz.sadora.contract.PetPose
import uz.sadora.contract.PetProduct
import uz.sadora.contract.PetState
import uz.sadora.contract.PetTrigger

/**
 * The pet, from the app's side: a free account never asks the server for a tip and only
 * sees the pet asleep now and then; a Premium one shows what the server says, one bubble
 * at a time.
 */
class PetControllerTest {

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private val day = LocalDate(2026, 10, 7)

    @Test
    fun `a free account never asks for a tip and sees the pet asleep every few days`() = runTest {
        val recording = RecordingEngine { json(encode(PetState(PetKind.OHU, active = false))) }
        val pet = PetController(graph(recording).petApi, AppState(), PromptPrefs.InMemory())
        pet.load()
        assertFalse(pet.active)
        assertEquals(PetKind.OHU, pet.pet)

        pet.after(PetTrigger.WATER_GOAL)
        assertEquals(0, recording.countOf("/v1/pet/nudge"), "a free account sends no nudges")

        assertFalse(pet.maybeTease("u", day), "never on the first day")
        assertTrue(pet.maybeTease("u", day.plusDays(1)))
        val teaser = assertNotNull(pet.bubble)
        assertTrue(teaser.teaser)
        assertEquals(PetPose.SLEEP, teaser.pose)

        pet.dismiss()
        assertFalse(pet.maybeTease("u", day.plusDays(2)), "not again the next day")
        assertTrue(pet.maybeTease("u", day.plusDays(4)))
    }

    @Test
    fun `a Premium account shows the server's line — one bubble at a time`() = runTest {
        val nudge = PetNudge(PetKind.LAYLO, PetPose.THINK, "Suv iching", PetAction.WATER)
        val recording = RecordingEngine { request ->
            if (request.url.encodedPath.endsWith("/nudge")) json(encode(PetNudgeAnswer(nudge)))
            else json(encode(PetState(PetKind.LAYLO, active = true)))
        }
        val pet = PetController(graph(recording).petApi, AppState(), PromptPrefs.InMemory())
        pet.load()
        assertTrue(pet.active)
        assertFalse(pet.maybeTease("u", day.plusDays(9)), "Premium is never teased")

        pet.after(PetTrigger.APP_OPEN)
        val shown = assertNotNull(pet.bubble)
        assertEquals("Suv iching", shown.text)
        assertEquals(PetAction.WATER, shown.action)

        pet.after(PetTrigger.MOOD_GOOD)
        assertEquals(1, recording.countOf("/v1/pet/nudge"), "no second request while a bubble is up")
        pet.dismiss()
        assertNull(pet.bubble)
    }

    /** A legendary pet's moments: a low mood is comforted, a badge celebrated, water shared. */
    @Test
    fun `a nudge carries the moment its action calls for`() = runTest {
        val nudge = PetNudge(PetKind.HUMO, PetPose.THINK, "Men shu yerdaman", PetAction.MIND_JOURNAL)
        val recording = RecordingEngine { request ->
            if (request.url.encodedPath.endsWith("/nudge")) json(encode(PetNudgeAnswer(nudge)))
            else json(encode(PetState(PetKind.HUMO, active = true, owned = listOf(PetKind.HUMO))))
        }
        val pet = PetController(graph(recording).petApi, AppState(), PromptPrefs.InMemory())
        pet.load()
        pet.after(PetTrigger.MOOD_LOW)
        assertEquals(PetMoment.COMFORT, assertNotNull(pet.bubble).moment)
        pet.dismiss()
        pet.after(PetTrigger.BADGE_EARNED)
        assertEquals(PetMoment.CELEBRATE, assertNotNull(pet.bubble).moment)
        pet.dismiss()
        pet.after(PetTrigger.MEAL_LOGGED)
        assertEquals(PetMoment.SNACK, assertNotNull(pet.bubble).moment)
    }

    private val humo = PetProduct(
        PetKind.HUMO,
        priceMinor = 49_900_000,
        appStoreProductId = "uz.sadora.pet.humo",
        googlePlayProductId = "uz.sadora.pet.humo",
        providers = listOf(PaymentProvider.PAYME, PaymentProvider.GOOGLE_PLAY),
    )

    /** Payme: the link opens, the app waits for the money, and then the pet is hers. */
    @Test
    fun `a legendary pet paid by Payme is hers once the payment lands`() = runTest {
        var paid = false
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/pet/checkout" -> json(encode(CheckoutSession("tx-1", PaymentProvider.PAYME, "https://pay/1", 49_900_000)))
                "/v1/billing/payments/tx-1" -> {
                    val state = if (paid) PaymentState.PAID else PaymentState.PENDING.also { paid = true }
                    json(encode(PaymentStatus("tx-1", state, PaymentProvider.PAYME, null, 49_900_000)))
                }
                else -> json(
                    encode(
                        if (paid) PetState(PetKind.HUMO, active = false, owned = listOf(PetKind.HUMO))
                        else PetState(PetKind.LAYLO, active = false, shop = listOf(humo)),
                    ),
                )
            }
        }
        val pet = PetController(graph(recording).petApi, AppState(), PromptPrefs.InMemory())
        pet.load()
        assertEquals(humo, pet.forSale)
        val session = assertNotNull(pet.checkout(PetKind.HUMO, PaymentProvider.PAYME))
        assertEquals("https://pay/1", session.url)
        assertTrue(pet.awaitPayment())
        assertEquals(listOf(PetKind.HUMO), pet.owned)
        assertEquals(PetKind.HUMO, pet.pet)
        assertNull(pet.forSale)
    }

    private class FakeStore(private val outcome: StoreOutcome) : StoreBilling {
        override val provider = PaymentProvider.GOOGLE_PLAY
        val finished = mutableListOf<String>()
        var bought: String? = null
        override suspend fun prices(productIds: List<String>) = productIds.associateWith { "499 000 so'm" }
        override suspend fun purchase(productId: String, accountId: String) = error("a pet is not a subscription")
        override suspend fun purchaseKeepsake(productId: String, accountId: String): StoreOutcome {
            bought = productId
            return outcome
        }
        override suspend fun owned() = emptyList<StoreReceipt>()
        override suspend fun finish(receipt: StoreReceipt) { finished += receipt.token }
    }

    /** A store build: the store's sheet sells it, the server checks the receipt, and only then is it finished. */
    @Test
    fun `a legendary pet bought in the store is finished only after the server accepts it`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/pet/store" -> json(encode(PetState(PetKind.HUMO, owned = listOf(PetKind.HUMO))))
                else -> json(encode(PetState(PetKind.NILUFAR, shop = listOf(humo))))
            }
        }
        val store = FakeStore(StoreOutcome.Purchased(StoreReceipt("uz.sadora.pet.humo", "tok-h", needsFinish = true)))
        val pet = PetController(graph(recording).petApi, AppState(), PromptPrefs.InMemory(), store = store, currentUserId = { "u-1" })
        pet.load()
        assertTrue(pet.buyInStore(humo))
        assertEquals("uz.sadora.pet.humo", store.bought)
        assertEquals(listOf("tok-h"), store.finished)
        assertEquals(listOf(PetKind.HUMO), pet.owned)
    }

    /** The one-off offer: shown once, as Humo's own bubble, and reported seen at once. */
    @Test
    fun `the offer shows once and is reported seen`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/pet/offer/seen" -> json(encode(PetState(PetKind.NILUFAR, active = true, shop = listOf(humo))))
                else -> json(encode(PetState(PetKind.NILUFAR, active = true, shop = listOf(humo), offerDue = true)))
            }
        }
        val pet = PetController(graph(recording).petApi, AppState(), PromptPrefs.InMemory())
        pet.load()
        assertTrue(pet.maybeOffer())
        val offer = assertNotNull(pet.bubble)
        assertTrue(offer.offer)
        assertEquals(PetKind.HUMO, offer.pet)
        assertEquals(1, recording.countOf("/v1/pet/offer/seen"))
        pet.dismiss()
        assertFalse(pet.maybeOffer(), "once")
    }

    private fun LocalDate.plusDays(days: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() + days)
}
