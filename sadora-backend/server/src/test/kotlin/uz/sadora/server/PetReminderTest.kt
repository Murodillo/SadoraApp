package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uz.sadora.contract.Language
import uz.sadora.contract.PetKind
import uz.sadora.server.pet.PetPhrases

class PetReminderTest {
    @Test
    fun theTimeIsAlwaysInTheReminder() {
        Language.entries.forEach { language ->
            PetKind.entries.forEach { pet ->
                val text = PetPhrases.medReminder(language, pet, "08:30")
                assertTrue("08:30" in text, "$language/$pet: $text")
            }
        }
    }

    @Test
    fun theTitleIsTheNameTheAppShows() {
        assertEquals("Ohu", PetPhrases.name(Language.UZ, PetKind.OHU))
        assertEquals("Момик", PetPhrases.name(Language.RU, PetKind.MOMIQ))
    }
}
