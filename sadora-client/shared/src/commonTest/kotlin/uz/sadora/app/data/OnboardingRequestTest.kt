package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import uz.sadora.app.model.AppState
import uz.sadora.app.model.Goal
import uz.sadora.app.model.LifeStage

/**
 * What the answers collected by the registration flow turn into on the wire.
 *
 * The server validates this request and rejects the whole of it on one bad field, so
 * the shape of what the questions produce is worth pinning: a blank name is the one
 * value it refuses outright, which is why that question carries no skip.
 */
class OnboardingRequestTest {

    private fun answered(): AppState = AppState().apply {
        name = "Malika"
        birthDate = "14.03.1994"
        lifeStage = LifeStage.Cycle
        averageCycleLength = 29
        averagePeriodLength = 6
        goals.addAll(listOf(Goal.UnderstandCycle, Goal.SleepBetter))
        heightCm = "164"
        weightKg = "58"
        markedPeriodDays.add(LocalDate.parse("2026-08-30"))
        notificationsAllowed = true
        healthDataAllowed = true
        cameraAllowed = false
        consentStoreHealth = true
        consentTerms = true
        consentAnalytics = false
    }

    @Test
    fun `the flow's answers reach the wire whole`() {
        val request = answered().toOnboardingRequest("Asia/Tashkent")

        assertEquals("Malika", request.name)
        assertEquals(uz.sadora.contract.LifeStage.CYCLE, request.lifeStage)
        assertEquals("Asia/Tashkent", request.timezone)
        assertEquals(LocalDate.parse("1994-03-14"), request.birthDate)
        assertEquals(164, request.heightCm)
        assertEquals(58, request.weightKg)
        assertEquals(2, request.goals.size)

        val cycle = assertNotNull(request.cycle, "a cycle stage carries its baseline")
        assertEquals(29, cycle.averageCycleLength)
        assertEquals(6, cycle.averagePeriodLength)
        assertEquals(LocalDate.parse("2026-08-30"), cycle.lastPeriodStart)

        assertTrue(request.permissions.notifications)
        assertTrue(request.permissions.healthData)
        assertTrue(!request.permissions.camera)
        assertTrue(request.consents.storeHealth)
    }

    /**
     * The two answers that used to be lost between the question and the server: the
     * referral, and the check-in taken before she had an account. The symptom tiles are
     * labels on screen and catalogue keys on the wire.
     */
    @Test
    fun `the referral and the first check-in reach the wire`() {
        val request = answered().apply {
            referredByDoctor = true
            mood = uz.sadora.app.model.Mood.Low
            moodAnswered = true
            // The tiles record the key as they are tapped, so what is sent does not
            // depend on which language she read the words in.
            toggleStarterSymptom("fatigue", "Charchoq")
            toggleStarterSymptom("headache", "Bosh og'rig'i")
        }.toOnboardingRequest("Asia/Tashkent")

        assertEquals(true, request.referredByDoctor)
        val checkIn = assertNotNull(request.firstCheckIn)
        assertEquals(uz.sadora.contract.MoodLevel.LOW, checkIn.mood)
        assertEquals(listOf("fatigue", "headache"), checkIn.symptomKeys)
    }

    /** A skipped feeling question must not send the store's default mood as if she had answered. */
    @Test
    fun `an unanswered check-in sends nothing`() {
        val request = answered().toOnboardingRequest("Asia/Tashkent")
        assertNull(request.referredByDoctor)
        assertNull(request.firstCheckIn)
    }

    /**
     * The name question has no skip precisely because of this: the server refuses a
     * blank name, and the refusal would only arrive at the last screen of the run.
     */
    @Test
    fun `a name is the one answer the request cannot carry empty`() {
        val request = answered().apply { name = "  " }.toOnboardingRequest("Asia/Tashkent")
        assertTrue(request.name.isBlank(), "nothing client-side invents a name")
    }

    /** A stage that does not cycle sends no cycle baseline for the server to store. */
    @Test
    fun `a non-cycling stage sends no cycle baseline`() {
        val request = answered().apply {
            lifeStage = LifeStage.Menopause
        }.toOnboardingRequest("Asia/Tashkent")

        assertNull(request.cycle)
        assertEquals(uz.sadora.contract.LifeStage.MENOPAUSE, request.lifeStage)
    }

    /**
     * `+998901234567` on the wire is nine digits in the field, which draws them as
     * `90 123 45 67` itself.
     */
    @Test
    fun `the profile's phone comes back as the digits the field holds`() {
        assertEquals("901234567", "+998901234567".toLocalPhone())
        assertEquals("912345678", "998912345678".toLocalPhone())
        // Anything that is not a full local number is left as the digits it had.
        assertEquals("12345", "12345".toLocalPhone())
    }
}
