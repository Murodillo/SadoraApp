package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant
import kotlin.uuid.Uuid
import uz.sadora.contract.Language
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.server.notify.DiscreetPush
import uz.sadora.server.notify.OutboxRecord

class DiscreetPushTest {
    private fun record(category: NotificationCategory, link: String? = null) = OutboxRecord(
        id = Uuid.random(),
        userId = Uuid.random(),
        category = category,
        title = "Hayzingiz ertaga",
        body = "Folat, 1 tabletka — homiladorlik uchun",
        scheduledFor = Instant.fromEpochSeconds(1_800_000_000),
        status = NotificationStatus.QUEUED,
        sentAt = null,
        suppressedReason = null,
        link = link,
    )

    @Test
    fun `a reminder keeps where it leads but says nothing about her`() {
        val original = record(NotificationCategory.MED_REMINDER, link = "sadora://dose/x/2026-10-16/08:00")
        val neutral = DiscreetPush.neutral(original, Language.UZ)
        assertEquals("Sadora", neutral.title)
        assertEquals("Sizda yangi eslatma bor", neutral.body)
        assertEquals(original.link, neutral.link, "a tap still opens the right screen")
        assertEquals(original.id, neutral.id)
    }

    @Test
    fun `a message is called a message, in her language`() {
        val partner = DiscreetPush.neutral(record(NotificationCategory.PARTNER), Language.RU)
        assertEquals("У вас новое сообщение", partner.body)
        val dm = DiscreetPush.neutral(record(NotificationCategory.SYSTEM, link = "sadora://conversation/1"), Language.EN)
        assertEquals("You have a new message", dm.body)
    }

    @Test
    fun `no word of the original survives`() {
        Language.entries.forEach { language ->
            NotificationCategory.entries.forEach { category ->
                val neutral = DiscreetPush.neutral(record(category), language)
                listOf("Hayz", "Folat", "homilador").forEach { word ->
                    assertFalse(word in neutral.title || word in neutral.body, "$language $category: $neutral")
                }
            }
        }
    }
}
