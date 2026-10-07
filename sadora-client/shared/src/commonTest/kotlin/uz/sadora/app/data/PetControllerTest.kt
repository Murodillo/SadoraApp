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
import uz.sadora.contract.PetAction
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetNudge
import uz.sadora.contract.PetNudgeAnswer
import uz.sadora.contract.PetPose
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

    private fun LocalDate.plusDays(days: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() + days)
}
