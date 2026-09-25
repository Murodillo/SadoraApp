package uz.sadora.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import uz.sadora.app.ui.components.acceptDigits
import uz.sadora.app.ui.components.acceptPhone
import uz.sadora.app.ui.components.acceptText
import uz.sadora.app.ui.components.parseTypedDate
import uz.sadora.app.ui.components.parseTypedTime
import uz.sadora.app.ui.components.phoneIsComplete
import uz.sadora.contract.Limits

/**
 * What a field will take, before anything is sent.
 *
 * The composable half — which message is shown and when — is the screens' business;
 * what is pinned here is the part that decides what a field can hold at all, because
 * that is what stops a request the server is going to refuse.
 */
class FieldRulesTest {

    /**
     * The field stores digits and draws the mask, so the caret does not move when a
     * space is inserted — storing the formatted string made editing the middle of a
     * number produce nonsense.
     */
    @Test
    fun `the phone field holds digits and never more than nine`() {
        assertEquals("901234567", acceptPhone("901234567"))
        assertEquals("901234567", acceptPhone("+998 90 123 45 67"))
        assertEquals("901234567", acceptPhone("9012345678901234"))
        assertEquals("90123456", acceptPhone("90123456"))
        assertEquals("", acceptPhone("abc"))
    }

    @Test
    fun `only a complete number behind a real code opens the button`() {
        assertEquals(true, phoneIsComplete("901234567"))
        assertEquals(false, phoneIsComplete("90123456"))
        assertEquals(false, phoneIsComplete("701234567"))
    }

    @Test
    fun `a number field takes digits only and no wider than it is`() {
        assertEquals("164", acceptDigits("164", 3))
        assertEquals("164", acceptDigits("1o64cm", 3))
        assertEquals("999", acceptDigits("99999", 3))
        assertEquals("", acceptDigits("kg", 3))
    }

    @Test
    fun `a text field cannot exceed the column behind it`() {
        val long = "x".repeat(Limits.NAME_MAX + 40)
        assertEquals(Limits.NAME_MAX, acceptText(long, Limits.NAME_MAX).length)
        assertEquals("Malika", acceptText("Malika", Limits.NAME_MAX))
    }

    /** A date typed on a phone keyboard is typed in a hurry, so the parser is lenient. */
    @Test
    fun `a typed date is read the way it is typed`() {
        val expected = LocalDate(2026, 8, 27)
        listOf("27.8.2026", "27.08.2026", "27/8/2026", "27-08-2026").forEach {
            assertEquals(expected, parseTypedDate(it), "failed for: $it")
        }
    }

    @Test
    fun `a date that is not a date is null rather than nearly right`() {
        listOf("", "27.8", "32.8.2026", "27.13.2026", "31.2.2026", "27.8.1815", "x.y.z")
            .forEach { assertNull(parseTypedDate(it), "accepted: $it") }
    }

    @Test
    fun `a typed time is read and an impossible one is refused`() {
        assertEquals(LocalTime(10, 30), parseTypedTime("10:30"))
        assertEquals(LocalTime(9, 5), parseTypedTime("9.05"))
        listOf("", "10", "24:00", "10:60", "half ten").forEach {
            assertNull(parseTypedTime(it), "accepted: $it")
        }
    }
}
