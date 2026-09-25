package uz.sadora.server

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import uz.sadora.server.flags.FeatureFlagService.Companion.bucketOf
import uz.sadora.server.flags.FeatureFlagService.Companion.inRollout

class FeatureFlagRolloutTest {

    @Test
    fun `bucket is stable for the same user and flag`() {
        val userId = Uuid.parse("11111111-2222-3333-4444-555555555555")
        val first = bucketOf(userId, "food_scan")
        repeat(50) { assertEquals(first, bucketOf(userId, "food_scan")) }
    }

    @Test
    fun `bucket is always within range`() {
        repeat(500) {
            val bucket = bucketOf(Uuid.random(), "ai_chat_enabled")
            assertTrue(bucket in 0..99, "bucket $bucket outside 0..99")
        }
    }

    /**
     * The property that makes a staged rollout safe: nobody who already has the feature
     * loses it when the percentage goes up.
     */
    @Test
    fun `widening a rollout never removes a user`() {
        val users = List(300) { Uuid.random() }
        var previous = users.filter { inRollout(it, "food_scan", 5) }.toSet()
        listOf(10, 25, 50, 75, 100).forEach { percentage ->
            val current = users.filter { inRollout(it, "food_scan", percentage) }.toSet()
            assertTrue(
                previous.all { it in current },
                "widening to $percentage% dropped a user who was already in",
            )
            previous = current
        }
    }

    @Test
    fun `zero and one hundred percent are absolute`() {
        repeat(100) {
            val userId = Uuid.random()
            assertFalse(inRollout(userId, "any", 0))
            assertTrue(inRollout(userId, "any", 100))
        }
    }

    /** A 20% rollout that actually hits 2% or 60% would make staged releases useless. */
    @Test
    fun `distribution is close to the requested percentage`() {
        val sampleSize = 4000
        val users = List(sampleSize) { Uuid.random() }
        listOf(5, 20, 50, 80).forEach { percentage ->
            val hits = users.count { inRollout(it, "ai_chat_enabled", percentage) }
            val actual = hits * 100.0 / sampleSize
            assertTrue(
                abs(actual - percentage) < 4.0,
                "requested $percentage%, got ${"%.1f".format(actual)}%",
            )
        }
    }

    /** Two experiments must not select the same users, or their results correlate. */
    @Test
    fun `different flags bucket the same user independently`() {
        val users = List(600) { Uuid.random() }
        val inFirst = users.filter { inRollout(it, "food_scan", 50) }.toSet()
        val inSecond = users.filter { inRollout(it, "community", 50) }.toSet()
        val overlap = inFirst.count { it in inSecond } * 100.0 / users.size
        // Independent 50/50 splits overlap on about a quarter of the population.
        assertTrue(overlap in 18.0..32.0, "overlap ${"%.1f".format(overlap)}% looks correlated")
    }
}
