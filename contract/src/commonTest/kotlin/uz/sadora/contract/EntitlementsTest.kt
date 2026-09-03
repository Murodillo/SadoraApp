package uz.sadora.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class EntitlementsTest {

    private fun entitlements(vararg features: FeatureEntitlement) = Entitlements(
        tier = SubscriptionTier.FREE,
        features = features.toList(),
        evaluatedAt = Instant.fromEpochSeconds(0),
    )

    @Test
    fun `an unmetered feature reports no remaining count`() {
        val feature = FeatureEntitlement(key = FeatureKeys.NUTRITION_LOG, enabled = true)
        assertNull(feature.remainingToday)
        assertNull(feature.remainingThisMonth)
        assertTrue(feature.available)
    }

    @Test
    fun `remaining counts down and stops at zero`() {
        val feature = FeatureEntitlement(
            key = FeatureKeys.AI_CHAT,
            enabled = true,
            dailyLimit = 3,
            usedToday = 2,
        )
        assertEquals(1, feature.remainingToday)
        assertFalse(feature.copy(usedToday = 3).available)
        // Usage can exceed the limit if an admin lowers it mid-day; that must not
        // produce a negative remaining count.
        assertEquals(0, feature.copy(usedToday = 9).remainingToday)
    }

    @Test
    fun `the monthly limit can exhaust a feature that still has daily budget`() {
        val feature = FeatureEntitlement(
            key = FeatureKeys.AI_CHAT,
            enabled = true,
            dailyLimit = 20,
            monthlyLimit = 400,
            usedToday = 1,
            usedThisMonth = 400,
        )
        assertEquals(19, feature.remainingToday)
        assertFalse(feature.available)
    }

    @Test
    fun `a disabled feature is never available`() {
        val feature = FeatureEntitlement(key = FeatureKeys.LEARN_PREMIUM, enabled = false)
        assertFalse(feature.available)
    }

    /** A client on a newer server must treat a key it does not know as closed. */
    @Test
    fun `an unknown feature key reads as unavailable`() {
        val resolved = entitlements(FeatureEntitlement(FeatureKeys.AI_CHAT, enabled = true))
        assertFalse(resolved.isAvailable("some_feature_shipped_later"))
        assertTrue(resolved.isAvailable(FeatureKeys.AI_CHAT))
    }

    @Test
    fun `only cycle stages predict a cycle`() {
        assertTrue(LifeStage.CYCLE.predictsCycle)
        assertTrue(LifeStage.TRYING_TO_CONCEIVE.predictsCycle)
        listOf(
            LifeStage.PREGNANCY,
            LifeStage.POSTPARTUM,
            LifeStage.PERIMENOPAUSE,
            LifeStage.MENOPAUSE,
        ).forEach { assertFalse(it.predictsCycle, "$it should not predict a cycle") }
    }
}
