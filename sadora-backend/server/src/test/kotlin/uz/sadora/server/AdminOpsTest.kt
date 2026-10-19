package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalTime
import uz.sadora.contract.AppStatusRequest
import uz.sadora.contract.AppUpdate
import uz.sadora.contract.FrequencyCaps
import uz.sadora.contract.Language
import uz.sadora.contract.Platform
import uz.sadora.server.admin.AppReleasePolicy
import uz.sadora.server.admin.AppReleaseService
import uz.sadora.server.admin.CampaignDecision
import uz.sadora.server.admin.CampaignRecipient
import uz.sadora.server.admin.CampaignService
import uz.sadora.server.admin.CampaignText
import uz.sadora.server.admin.Maintenance
import uz.sadora.server.admin.PlatformRelease
import uz.sadora.server.admin.SystemHealthService
import uz.sadora.server.core.ValidationException

/** The pure rules behind the system health page, the release policy and campaigns. */
class AdminOpsTest {

    private val noon: Instant = Instant.parse("2026-10-20T07:00:00Z") // 12:00 in Tashkent

    @Test
    fun `a service is off without keys, in error when its last try failed, warned when it recovered`() {
        val earlier = noon - 2.hours
        assertEquals("off", SystemHealthService.statusOf(false, 3, null, noon))
        assertEquals("error", SystemHealthService.statusOf(true, 1, earlier, noon))
        assertEquals("error", SystemHealthService.statusOf(true, 1, null, noon))
        assertEquals("warn", SystemHealthService.statusOf(true, 1, noon, earlier))
        assertEquals("ok", SystemHealthService.statusOf(true, 0, earlier, null))
    }

    @Test
    fun `a build below the minimum must update, below the latest may, and unknown is left alone`() {
        val policy = AppReleasePolicy(android = PlatformRelease(minBuild = 10, latestBuild = 12, storeUrl = "https://play"))
        fun update(build: Int?, platform: Platform = Platform.ANDROID) =
            AppReleaseService.statusFor(policy, AppStatusRequest(platform, build), Language.UZ).update
        assertEquals(AppUpdate.REQUIRED, update(9))
        assertEquals(AppUpdate.SUGGESTED, update(10))
        assertEquals(AppUpdate.NONE, update(12))
        assertEquals(AppUpdate.NONE, update(null))
        assertEquals(AppUpdate.NONE, update(1, Platform.WEB))
    }

    @Test
    fun `maintenance speaks her language and falls back to Uzbek`() {
        val policy = AppReleasePolicy(maintenance = Maintenance(true, mapOf("uz" to "Ishlar", "ru" to "Работы")))
        assertEquals("Работы", AppReleaseService.statusFor(policy, AppStatusRequest(Platform.IOS, 3), Language.RU).message)
        assertEquals("Ishlar", AppReleaseService.statusFor(policy, AppStatusRequest(Platform.IOS, 3), Language.EN).message)
        assertEquals(null, AppReleaseService.statusFor(AppReleasePolicy(), AppStatusRequest(Platform.IOS, 3), Language.RU).message)
    }

    @Test
    fun `the release policy refuses what would lock everyone out or point nowhere`() {
        assertFailsWith<ValidationException> { AppReleaseService.validate(AppReleasePolicy(ios = PlatformRelease(minBuild = 0))) }
        assertFailsWith<ValidationException> { AppReleaseService.validate(AppReleasePolicy(ios = PlatformRelease(minBuild = 5, latestBuild = 4))) }
        assertFailsWith<ValidationException> { AppReleaseService.validate(AppReleasePolicy(ios = PlatformRelease(storeUrl = "http://x"))) }
        assertFailsWith<ValidationException> { AppReleaseService.validate(AppReleasePolicy(maintenance = Maintenance(true, mapOf("en" to "x")))) }
        val cleaned = AppReleaseService.validate(AppReleasePolicy(maintenance = Maintenance(false, mapOf("uz" to "  ", "ru" to " Работы "))))
        assertEquals(mapOf("ru" to "Работы"), cleaned.maintenance.message)
    }

    private fun recipient(
        enabled: Boolean = true,
        news: Boolean = true,
        discreet: Boolean = false,
        hasDevice: Boolean = true,
        sentToday: Int = 0,
        sentThisWeek: Int = 0,
        quietFrom: LocalTime? = null,
        quietUntil: LocalTime? = null,
    ) = CampaignRecipient(Uuid.random(), Language.UZ, "Asia/Tashkent", discreet, hasDevice, enabled, news, quietFrom, quietUntil, sentToday, sentThisWeek)

    private val caps = FrequencyCaps(maxPerDay = 3, maxPerWeek = 10)

    @Test
    fun `a campaign leaves out who said no, then who cannot be rung, then who has had enough`() {
        fun reason(recipient: CampaignRecipient) = (CampaignService.decide(recipient, noon, caps) as CampaignDecision.Skip).reason
        assertEquals("notifications_off", reason(recipient(enabled = false, news = false)))
        assertEquals("news_off", reason(recipient(news = false)))
        assertEquals("discreet", reason(recipient(discreet = true)))
        assertEquals("no_device", reason(recipient(hasDevice = false)))
        assertEquals("daily_cap", reason(recipient(sentToday = 3)))
        assertEquals("weekly_cap", reason(recipient(sentThisWeek = 10)))
        assertEquals(CampaignDecision.Queue(noon), CampaignService.decide(recipient(), noon, caps))
    }

    @Test
    fun `quiet hours postpone a campaign to their end, across midnight too`() {
        // 12:00 inside 11:00–13:00: today at 13:00 Tashkent.
        val midday = CampaignService.decide(recipient(quietFrom = LocalTime(11, 0), quietUntil = LocalTime(13, 0)), noon, caps)
        assertEquals(CampaignDecision.Queue(Instant.parse("2026-10-20T08:00:00Z")), midday)
        // 23:30 inside 22:00–07:00: tomorrow at 07:00.
        val night = Instant.parse("2026-10-20T18:30:00Z")
        val overnight = CampaignService.decide(recipient(quietFrom = LocalTime(22, 0), quietUntil = LocalTime(7, 0)), night, caps)
        assertEquals(CampaignDecision.Queue(Instant.parse("2026-10-21T02:00:00Z")), overnight)
    }

    @Test
    fun `campaign texts need Uzbek, both halves, and fit a lock screen`() {
        assertFailsWith<ValidationException> { CampaignService.validateTexts(mapOf("ru" to CampaignText("a", "b"))) }
        assertFailsWith<ValidationException> { CampaignService.validateTexts(mapOf("uz" to CampaignText("a", "b"), "en" to CampaignText("a", ""))) }
        assertFailsWith<ValidationException> { CampaignService.validateTexts(mapOf("uz" to CampaignText("a".repeat(66), "b"))) }
        val texts = CampaignService.validateTexts(mapOf("uz" to CampaignText(" Salom ", "Matn"), "en" to CampaignText("", ""), "de" to CampaignText("x", "y")))
        assertEquals(setOf("uz"), texts.keys)
        assertEquals("Salom", CampaignService.textFor(texts, Language.EN).title)
    }
}
