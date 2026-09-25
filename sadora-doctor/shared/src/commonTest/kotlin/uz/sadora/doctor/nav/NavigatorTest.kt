package uz.sadora.doctor.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The back stack over the panel, and the depth the route transition reads its direction from. */
class NavigatorTest {

    @Test
    fun `the panel is the root and back empties the stack one screen at a time`() {
        val navigator = Navigator()
        assertEquals(Route.Panel, navigator.current)
        assertFalse(navigator.canGoBack)

        navigator.push(Route.MyPage("doc-1"))
        navigator.push(Route.Question("q1"))
        assertEquals(Screen(Route.Question("q1"), 2), navigator.screen)

        navigator.pop()
        assertEquals(Screen(Route.MyPage("doc-1"), 1), navigator.screen)
        navigator.pop()
        assertEquals(Route.Panel, navigator.current)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun `a double tap opens a screen once`() {
        val navigator = Navigator()
        navigator.push(Route.Apply)
        navigator.push(Route.Apply)
        navigator.push(Route.Panel)
        assertEquals(1, navigator.depth)
    }

    @Test
    fun `changing phase clears the stack`() {
        val navigator = Navigator()
        assertEquals(AppPhase.Splash, navigator.phase)
        navigator.goTo(AppPhase.Main)
        navigator.push(Route.NewPost)
        assertTrue(navigator.canGoBack)

        navigator.goTo(AppPhase.SignIn)

        assertEquals(AppPhase.SignIn, navigator.phase)
        assertEquals(Route.Panel, navigator.current)
    }
}
