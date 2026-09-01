package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.datetime.LocalTime
import uz.sadora.contract.FrequencyCaps
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationSettings
import uz.sadora.contract.SuppressionReasons
import uz.sadora.server.notify.DeliveryDecision
import uz.sadora.server.notify.NotificationPolicy

class NotificationPolicyTest {

    private fun time(text: String) = LocalTime.parse(text)

    private val caps = FrequencyCaps(maxPerDay = 3, maxPerWeek = 10)

    private fun decide(
        category: NotificationCategory = NotificationCategory.WATER,
        at: String = "12:00",
        settings: NotificationSettings = NotificationSettings(),
        sentToday: Int = 0,
        sentThisWeek: Int = 0,
        hasDevice: Boolean = true,
    ) = NotificationPolicy.decide(category, time(at), settings, sentToday, sentThisWeek, caps, hasDevice)

    // ---------------------------------------------------------------- quiet hours

    @Test
    fun `a quiet window that wraps midnight covers both sides of it`() {
        val night = time("22:00")
        val morning = time("07:00")
        listOf("22:00", "23:30", "00:10", "03:00", "06:59").forEach {
            assertTrue(NotificationPolicy.isQuiet(time(it), night, morning), "$it should be quiet")
        }
        listOf("07:00", "12:00", "21:59").forEach {
            assertTrue(!NotificationPolicy.isQuiet(time(it), night, morning), "$it should not be quiet")
        }
    }

    @Test
    fun `a same-day quiet window behaves normally`() {
        val from = time("13:00")
        val until = time("15:00")
        assertTrue(NotificationPolicy.isQuiet(time("14:00"), from, until))
        assertTrue(!NotificationPolicy.isQuiet(time("12:59"), from, until))
        assertTrue(!NotificationPolicy.isQuiet(time("15:00"), from, until), "the end is exclusive")
    }

    /** Equal ends would otherwise silence the entire day. */
    @Test
    fun `a zero-length window silences nothing`() {
        assertTrue(!NotificationPolicy.isQuiet(time("03:00"), time("22:00"), time("22:00")))
    }

    @Test
    fun `no window set means never quiet`() {
        assertTrue(!NotificationPolicy.isQuiet(time("03:00"), null, null))
        assertTrue(!NotificationPolicy.isQuiet(time("03:00"), time("22:00"), null))
    }

    // ---------------------------------------------------------------- decisions

    @Test
    fun `an ordinary notification is held during quiet hours`() {
        val settings = NotificationSettings(quietFrom = time("22:00"), quietUntil = time("07:00"))
        val decision = decide(at = "23:00", settings = settings)
        assertEquals(SuppressionReasons.QUIET_HOURS, assertIs<DeliveryDecision.Suppress>(decision).reason)
    }

    /**
     * The rule this whole class exists to protect: a dose reminder at the time she chose
     * is not promotional traffic, and holding it back is the app failing at its job.
     */
    @Test
    fun `a medication reminder ignores quiet hours and the caps`() {
        val settings = NotificationSettings(quietFrom = time("22:00"), quietUntil = time("07:00"))
        val decision = decide(
            category = NotificationCategory.MED_REMINDER,
            at = "23:00",
            settings = settings,
            sentToday = 99,
            sentThisWeek = 99,
        )
        assertIs<DeliveryDecision.Send>(decision)
    }

    /** But her own switch still wins — that is a decision, not a budget. */
    @Test
    fun `turning the category off silences even a medication reminder`() {
        val settings = NotificationSettings(
            categories = mapOf(NotificationCategory.MED_REMINDER to false),
        )
        val decision = decide(category = NotificationCategory.MED_REMINDER, settings = settings)
        assertEquals(SuppressionReasons.CATEGORY_OFF, assertIs<DeliveryDecision.Suppress>(decision).reason)
    }

    @Test
    fun `the master switch silences everything`() {
        val decision = decide(
            category = NotificationCategory.MED_REMINDER,
            settings = NotificationSettings(enabled = false),
        )
        assertEquals(
            SuppressionReasons.NOTIFICATIONS_OFF,
            assertIs<DeliveryDecision.Suppress>(decision).reason,
        )
    }

    @Test
    fun `the daily cap stops ordinary traffic at the limit, not after it`() {
        assertIs<DeliveryDecision.Send>(decide(sentToday = 2))
        assertEquals(
            SuppressionReasons.DAILY_CAP,
            assertIs<DeliveryDecision.Suppress>(decide(sentToday = 3)).reason,
        )
    }

    @Test
    fun `the weekly cap applies once the day still has room`() {
        assertEquals(
            SuppressionReasons.WEEKLY_CAP,
            assertIs<DeliveryDecision.Suppress>(decide(sentToday = 0, sentThisWeek = 10)).reason,
        )
    }

    @Test
    fun `nothing is queued for a user with no registered device`() {
        assertEquals(
            SuppressionReasons.NO_DEVICE,
            assertIs<DeliveryDecision.Suppress>(decide(hasDevice = false)).reason,
        )
    }

    @Test
    fun `an unconfigured category defaults to on`() {
        assertIs<DeliveryDecision.Send>(decide(category = NotificationCategory.INSIGHT))
    }
}
