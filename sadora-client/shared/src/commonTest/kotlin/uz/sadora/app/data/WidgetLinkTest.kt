package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uz.sadora.app.nav.AppLink

class WidgetLinkTest {
    @Test
    fun `a dose reminder's link names the dose`() {
        assertEquals(
            AppLink.Dose("0f8e7c1a-3b2d-4c5e-9f00-112233445566", "2026-10-16", "08:00"),
            AppLink.parse("sadora://dose/0f8e7c1a-3b2d-4c5e-9f00-112233445566/2026-10-16/08:00"),
        )
        assertNull(AppLink.parse("sadora://dose/x"))
    }

    @Test
    fun `a widget tap opens the screen behind the block`() {
        assertEquals(AppLink.Open(AppLink.Open.Target.Journey), AppLink.parse("sadora://open/journey"))
        assertEquals(AppLink.Open(AppLink.Open.Target.Medications), AppLink.parse("sadora://open/medications"))
        assertEquals(AppLink.Open(AppLink.Open.Target.Today), AppLink.parse("sadora://open/nonsense"))
        assertNull(AppLink.parse("https://sadora.app/open/journey"), "only the app's own scheme opens a screen")
    }
}
