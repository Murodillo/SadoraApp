package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uz.sadora.contract.HomeLayout
import uz.sadora.contract.HomeWidget
import uz.sadora.contract.HomeWidgets

class HomeLayoutTest {

    @Test
    fun `a layout saved before the body-signals card gains it switched on — after her own cards`() {
        val saved = HomeLayout(
            listOf(
                HomeWidget(HomeWidgets.STREAK, 0, true),
                HomeWidget(HomeWidgets.AI, 1, true),
            ),
        )
        val visible = saved.reconciled().visible()
        assertTrue(HomeWidgets.BODY_SIGNALS in visible)
        assertEquals(listOf(HomeWidgets.STREAK, HomeWidgets.AI), visible.take(2))
    }

    @Test
    fun `a new account sees body signals right after the score`() {
        val visible = HomeLayout().visible()
        assertEquals(visible.indexOf(HomeWidgets.SCORE) + 1, visible.indexOf(HomeWidgets.BODY_SIGNALS))
    }
}
