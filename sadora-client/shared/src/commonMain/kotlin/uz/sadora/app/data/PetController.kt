package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import uz.sadora.app.data.api.PetApi
import uz.sadora.app.model.AppState
import uz.sadora.app.model.AppStateSync
import uz.sadora.app.model.Meal
import uz.sadora.app.model.Mood
import uz.sadora.contract.PetAction
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetPose
import uz.sadora.contract.PetTrigger

/**
 * What the pet's bubble shows. A [teaser] has no text of its own: the pet is asleep and
 * the bubble is the screen's "wake me with Premium" line, never advice.
 */
data class PetBubble(
    val pet: PetKind,
    val pose: PetPose,
    val text: String?,
    val action: PetAction? = null,
    val teaser: Boolean = false,
)

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
) {
    private val calls = ApiCallState()

    var pet by mutableStateOf(PetKind.DEFAULT)
        private set

    /** Her plan includes the pet. Read from the server; until then, from the plan on screen. */
    var active by mutableStateOf(state.isPremium)
        private set

    var bubble by mutableStateOf<PetBubble?>(null)
        private set

    suspend fun load() {
        val api = api ?: return
        calls.run(silent = true) { api.state() }?.let {
            pet = it.pet
            active = it.active
        }
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
        if (bubble == null) {
            pet = nudge.pet
            bubble = PetBubble(nudge.pet, nudge.pose, nudge.text, nudge.action)
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

    private companion object {
        const val TeaseEveryDays = 3
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
            pet.fire(scope, PetTrigger.WATER_GOAL)
        }
    }

    override fun doseTaken(doseId: String) {
        inner.doseTaken(doseId)
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
