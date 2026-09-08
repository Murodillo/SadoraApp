package uz.sadora.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uz.sadora.app.model.AppLanguage

/**
 * The two documents a user agrees to, in the three languages she can read them in.
 *
 * The interface already guarantees every language answers; what it cannot guarantee is
 * that the answer is a translation and not the Uzbek pasted across, or that a section
 * was dropped on the way. Both are the kind of mistake that only shows up in a store
 * review, or in a complaint.
 */
class LegalTextsTest {

    private val languages = listOf(LegalTextsUz, LegalTextsRu, LegalTextsEn)

    @Test
    fun `every language carries the same documents, section for section`() {
        val reference = LegalTextsUz
        languages.forEach { legal ->
            assertEquals(reference.terms.size, legal.terms.size, "terms sections in $legal")
            assertEquals(reference.privacy.size, legal.privacy.size, "privacy sections in $legal")
            reference.terms.indices.forEach { index ->
                assertEquals(
                    reference.terms[index].body.size,
                    legal.terms[index].body.size,
                    "terms paragraph count at $index in $legal",
                )
            }
            reference.privacy.indices.forEach { index ->
                assertEquals(
                    reference.privacy[index].body.size,
                    legal.privacy[index].body.size,
                    "privacy paragraph count at $index in $legal",
                )
            }
        }
    }

    @Test
    fun `nothing is blank`() {
        languages.forEach { legal ->
            (legal.terms + legal.privacy).forEach { section ->
                assertTrue(section.heading.isNotBlank(), "blank heading in $legal")
                section.body.forEach { assertTrue(it.isNotBlank(), "blank paragraph in $legal") }
            }
        }
    }

    /** A translation that is character-for-character the Uzbek is not a translation. */
    @Test
    fun `the translations were actually translated`() {
        listOf(LegalTextsRu, LegalTextsEn).forEach { legal ->
            legal.terms.forEachIndexed { index, section ->
                assertNotEquals(LegalTextsUz.terms[index].heading, section.heading, "$legal terms $index")
            }
            legal.privacy.forEachIndexed { index, section ->
                assertNotEquals(LegalTextsUz.privacy[index].heading, section.heading, "$legal privacy $index")
            }
        }
    }

    /**
     * Which text binds her has to be on the screen carrying the text — and the Uzbek,
     * being the original, must not claim to be a translation of itself.
     */
    @Test
    fun `only the translations say they are translations`() {
        assertNull(LegalTextsUz.translationNotice)
        assertTrue(LegalTextsRu.translationNotice!!.isNotBlank())
        assertTrue(LegalTextsEn.translationNotice!!.isNotBlank())
    }

    @Test
    fun `the language the interface speaks is the language the documents are in`() {
        AppLanguage.entries.forEach { language ->
            assertEquals(LegalTexts.of(language), stringsFor(language).settings.legal, "$language")
        }
    }

    /**
     * The consent row stores the policy version, so a screen dated later than the version
     * recorded against it would make the record claim she agreed to something she never
     * saw. This pins the two together in the only place a test can reach.
     */
    @Test
    fun `the effective date is the same day the server calls the policy version`() {
        assertEquals("2026-09-03", LEGAL_EFFECTIVE_DATE)
        assertTrue("2026" in LegalTextsUz.effectiveDate, LegalTextsUz.effectiveDate)
        assertTrue("3" in LegalTextsUz.effectiveDate, LegalTextsUz.effectiveDate)
    }
}
