package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import uz.sadora.app.data.api.PetApi
import uz.sadora.app.model.AppState
import uz.sadora.app.model.AppStateSync
import uz.sadora.app.model.Meal
import uz.sadora.app.model.Mood
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.contract.PetAction
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetPose
import uz.sadora.contract.PetProduct
import uz.sadora.contract.PetState
import uz.sadora.contract.PetStorePurchase
import uz.sadora.contract.PetTrigger

/**
 * What the pet's bubble shows. A [teaser] has no text of its own: the pet is asleep and
 * the bubble is the screen's "wake me with Premium" line, never advice. An [offer] is the
 * legendary pet's one-off visit, with its own line and a way to the picker.
 */
data class PetBubble(
    val pet: PetKind,
    val pose: PetPose,
    val text: String?,
    val action: PetAction? = null,
    val teaser: Boolean = false,
    /** What the pet acts out with the line, where it has a loop for it. */
    val moment: PetMoment? = null,
    val offer: Boolean = false,
)

/**
 * The extra things a legendary pet acts out. Chosen here from what she just did; a pet
 * without a loop for one simply shows its pose.
 */
enum class PetMoment { COMFORT, CELEBRATE, SNACK }

/** The moment an action calls for, if any. */
fun PetTrigger.moment(): PetMoment? = when (this) {
    PetTrigger.MOOD_LOW -> PetMoment.COMFORT
    PetTrigger.BADGE_EARNED, PetTrigger.STREAK_KEPT -> PetMoment.CELEBRATE
    PetTrigger.WATER_GOAL, PetTrigger.MEAL_LOGGED, PetTrigger.FOOD_SCANNED -> PetMoment.SNACK
    else -> null
}

/** The kinds of small win a card can cheer; each card answers only its own. */
enum class Win { Water, Dose }

/**
 * The companion on this phone.
 *
 * The server decides whether a nudge is due — cooldowns live there so two phones do not
 * double the chatter — and this only asks after an action and holds the one bubble on
 * screen. For a free account it never asks: now and then, on opening the app, it shows
 * the pet asleep instead, and that is all a free account ever gets from it.
 */
class PetController(
    private val api: PetApi?,
    private val state: AppState,
    private val prompts: PromptPrefs = PromptPrefs.InMemory(),
    /** The store's sheet in a store build: the legendary pet is bought there, not by Payme or Click. */
    val store: StoreBilling? = null,
    /** Stamped on a store purchase so the server can tell whose it is. */
    private val currentUserId: () -> String? = { null },
) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    /** What the picker shows: the five, and the legendary one once it is hers or on sale. */
    var available by mutableStateOf(PetKind.free)
        private set

    var owned by mutableStateOf<List<PetKind>>(emptyList())
        private set

    /** The legendary pet on sale to her, or null — not on sale, or already hers. */
    var forSale by mutableStateOf<PetProduct?>(null)
        private set

    private var offerDue = false

    /** The store's localized price for [forSale], in a store build. */
    var storePrice by mutableStateOf<String?>(null)
        private set

    /** A Payme or Click payment while its page is open. */
    var paying by mutableStateOf<CheckoutSession?>(null)
        private set

    /** A store purchase that clears later — cash at a kiosk. */
    var storePending by mutableStateOf(false)
        private set

    var pet by mutableStateOf(PetKind.DEFAULT)
        private set

    /** Her plan includes the pet. Read from the server; until then, from the plan on screen. */
    var active by mutableStateOf(state.isPremium)
        private set

    var bubble by mutableStateOf<PetBubble?>(null)
        private set

    /**
     * Small wins cheered this session — a water goal, a dose, a badge. The cards that
     * carry the win watch it and let the pet hop there; nothing goes to the server.
     */
    var cheers by mutableStateOf(0)
        private set

    var lastWin by mutableStateOf<Win?>(null)
        private set

    fun cheer(win: Win) {
        if (!active) return
        lastWin = win
        cheers++
    }

    /** The last cheer a card on screen actually showed. */
    private var cheerShown = 0

    /** Called by the card that cheered [count]; its bubble would only say it twice. */
    fun cheerShown(count: Int) {
        cheerShown = count
    }

    suspend fun load() {
        val api = api ?: return
        calls.run(silent = true) { api.state() }?.let(::apply)
    }

    private fun apply(next: PetState) {
        pet = next.pet
        active = next.active
        available = next.available
        owned = next.owned
        forSale = next.shop.firstOrNull()
        offerDue = next.offerDue
    }

    /** Picks a pet: shown at once, put back if the server refuses. */
    suspend fun choose(next: PetKind): Boolean {
        val previous = pet
        pet = next
        val api = api ?: return true
        val saved = calls.run { api.choose(next) }
        if (saved == null) {
            pet = previous
            return false
        }
        pet = saved.pet
        active = saved.active
        return true
    }

    /** After an action. Quiet for a free account, and while a bubble is already up. */
    suspend fun after(trigger: PetTrigger, detail: String? = null) {
        val api = api ?: return
        if (!active || bubble != null) return
        val nudge = calls.run(silent = true) { api.nudge(trigger, detail) }?.nudge ?: return
        // A win a card already cheered gets no bubble on top: the pet is there already.
        if (trigger in CardWins && cheerShown == cheers) return
        if (bubble == null) {
            pet = nudge.pet
            bubble = PetBubble(nudge.pet, nudge.pose, nudge.text, nudge.action, moment = trigger.moment())
        }
    }

    fun fire(scope: CoroutineScope, trigger: PetTrigger, detail: String? = null) {
        if (!active || api == null) return
        scope.launch { after(trigger, detail) }
    }

    /**
     * The free account's glimpse: the pet asleep, every [TeaseEveryDays] days at most,
     * never on her first day. Returns whether it showed.
     */
    suspend fun maybeTease(userId: String, today: LocalDate): Boolean {
        if (active || api == null || bubble != null) return false
        val due = prompts.petTeaseAfter(userId)
        if (due == null) {
            prompts.setPetTeaseAfter(userId, today.plus(1, DateTimeUnit.DAY))
            return false
        }
        if (today < due) return false
        prompts.setPetTeaseAfter(userId, today.plus(TeaseEveryDays, DateTimeUnit.DAY))
        bubble = PetBubble(pet, PetPose.SLEEP, text = null, teaser = true)
        return true
    }

    fun dismiss() {
        bubble = null
    }

    // ---------------------------------------------------------------- the legendary pet

    /**
     * Its one-off visit, when the server says it is due: shown as its own bubble and
     * reported seen at once, so it never comes twice. Returns whether it showed.
     */
    suspend fun maybeOffer(): Boolean {
        val api = api ?: return false
        val product = forSale ?: return false
        if (!offerDue || bubble != null) return false
        offerDue = false
        bubble = PetBubble(product.pet, PetPose.HAPPY, text = null, offer = true)
        calls.run(silent = true) { api.offerSeen() }?.let(::apply)
        return true
    }

    fun storeProductId(product: PetProduct): String? = when (store?.provider) {
        PaymentProvider.GOOGLE_PLAY -> product.googlePlayProductId
        PaymentProvider.APP_STORE -> product.appStoreProductId
        else -> null
    }

    suspend fun loadStorePrice() {
        val store = store ?: return
        val id = forSale?.let(::storeProductId) ?: return
        storePrice = runCatching { store.keepsakePrices(listOf(id))[id] }.getOrNull()
    }

    /** Payme or Click: the checkout link to open. */
    suspend fun checkout(pet: PetKind, provider: PaymentProvider): CheckoutSession? {
        val api = api ?: return null
        val session = calls.run { api.checkout(pet, provider) } ?: return null
        paying = session
        return session
    }

    /** Waits for the provider's callback, the way the paywall does. True once it is hers. */
    suspend fun awaitPayment(): Boolean {
        val api = api ?: return false
        val session = paying ?: return false
        repeat(PollAttempts) {
            delay(PollIntervalMillis)
            val status = api.paymentStatus(session.transactionId).valueOrNull ?: return@repeat
            when (status.state) {
                PaymentState.PAID -> {
                    load()
                    // Last, and with nothing suspending after it: the screen waiting on this
                    // call is keyed on [paying], and clearing it cancels that wait.
                    paying = null
                    return true
                }
                PaymentState.CANCELLED, PaymentState.FAILED -> {
                    paying = null
                    return false
                }
                PaymentState.PENDING -> Unit
            }
        }
        paying = null
        return false
    }

    fun cancelCheckout() {
        paying = null
    }

    /**
     * Through the store's own sheet: bought for this account, verified by the server, and
     * only then finished — a purchase the server never saw stays unfinished, and the next
     * attempt finds it already owned and posts it again.
     */
    suspend fun buyInStore(product: PetProduct): Boolean {
        val api = api ?: return false
        val store = store ?: return false
        val productId = storeProductId(product) ?: return false
        val accountId = currentUserId() ?: return false
        storePending = false
        return when (val outcome = store.purchaseKeepsake(productId, accountId)) {
            is StoreOutcome.Purchased -> {
                val receipt = outcome.receipt
                val next = calls.run {
                    api.buyInStore(PetStorePurchase(product.pet, store.provider, receipt.productId, receipt.token))
                } ?: return false
                runCatching { store.finish(receipt) }
                apply(next)
                true
            }
            StoreOutcome.Pending -> {
                storePending = true
                false
            }
            StoreOutcome.Cancelled, is StoreOutcome.Failed -> false
        }
    }

    /**
     * A legendary pet bought in the store whose verification never landed: posted again,
     * and finished once the server has it. Nothing when nothing is unfinished.
     */
    suspend fun reconcileStore() {
        val api = api ?: return
        val store = store ?: return
        val receipts = runCatching { store.unfinishedKeepsakes() }.getOrDefault(emptyList())
        if (receipts.isEmpty()) return
        if (forSale == null) load()
        val product = forSale ?: return
        val productId = storeProductId(product) ?: return
        receipts.filter { it.productId == productId }.forEach { receipt ->
            val next = calls.run(silent = true) {
                api.buyInStore(PetStorePurchase(product.pet, store.provider, receipt.productId, receipt.token))
            } ?: return@forEach
            runCatching { store.finish(receipt) }
            storePending = false
            apply(next)
        }
    }

    private companion object {
        const val TeaseEveryDays = 3
        const val PollAttempts = 60
        const val PollIntervalMillis = 3_000L
        val CardWins = setOf(PetTrigger.WATER_GOAL, PetTrigger.MED_TAKEN)
    }
}

/**
 * Hears the store's edits on their way to the server and lets the pet answer the ones
 * worth a word. Every call is passed on first and unchanged, so the pet can never stand
 * between her and a save.
 */
class PetSyncTap(
    private val inner: AppStateSync,
    private val pet: PetController,
    private val state: AppState,
    private val scope: CoroutineScope,
) : AppStateSync by inner {

    override fun waterAdded(ml: Int) {
        inner.waterAdded(ml)
        val goal = state.waterGoalMl
        if (ml > 0 && goal > 0 && state.waterMl >= goal && state.waterMl - ml < goal) {
            pet.cheer(Win.Water)
            pet.fire(scope, PetTrigger.WATER_GOAL)
        }
    }

    override fun doseTaken(doseId: String) {
        inner.doseTaken(doseId)
        pet.cheer(Win.Dose)
        pet.fire(scope, PetTrigger.MED_TAKEN)
    }

    override fun mealLogged(meal: Meal) {
        inner.mealLogged(meal)
        if (meal.id.startsWith("scan-")) {
            pet.fire(scope, PetTrigger.FOOD_SCANNED, meal.description.takeIf { it.isNotBlank() })
        } else {
            pet.fire(scope, PetTrigger.MEAL_LOGGED)
        }
    }

    override fun checkInChanged(mood: Mood?, energy: Int?, stress: Int?) {
        inner.checkInChanged(mood, energy, stress)
        val score = mood?.score ?: return
        when {
            score <= 2 -> pet.fire(scope, PetTrigger.MOOD_LOW)
            score >= 4 -> pet.fire(scope, PetTrigger.MOOD_GOOD)
        }
    }
}
