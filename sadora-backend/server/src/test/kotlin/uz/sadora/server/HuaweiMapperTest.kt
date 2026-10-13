package uz.sadora.server

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import uz.sadora.server.wearable.huawei.HuaweiGroups
import uz.sadora.server.wearable.huawei.HuaweiHealthRecords
import uz.sadora.server.wearable.huawei.HuaweiMapper
import uz.sadora.server.wearable.huawei.HuaweiNotification

/** Huawei Health Kit's answers, in the shapes its reference shows, to SADORA samples. */
class HuaweiMapperTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val tashkent = TimeZone.of("+05:00")

    @Test
    fun `a day's statistics land on her own date, and an unworn day is left out`() {
        // 2026-09-04 00:00 +05:00, and the day after it with nothing worn.
        val groups = json.decodeFromString<HuaweiGroups>(
            """{"group":[
                 {"startTime":1788462000000,"endTime":1788548399999,"sampleSet":[{"dataCollectorId":"x","samplePoints":[
                   {"startTime":1788462000000000000,"endTime":1788548399999000000,"dataTypeName":"com.huawei.continuous.steps.total",
                    "value":[{"fieldName":"steps","integerValue":9000},{"fieldName":"duration","integerValue":80}]},
                   {"dataTypeName":"com.huawei.continuous.heart_rate.statistics",
                    "value":[{"fieldName":"avg","floatValue":72.5},{"fieldName":"max","floatValue":140.0},{"fieldName":"last","floatValue":66.0}]},
                   {"dataTypeName":"com.huawei.continuous.body_weight.statistics",
                    "value":[{"fieldName":"avg","floatValue":61.0},{"fieldName":"last","floatValue":61.4},{"fieldName":"avg_body_fat_rate","floatValue":24.5}]},
                   {"dataTypeName":"com.huawei.continuous.skin.temperature.statistics","value":[{"fieldName":"avg","floatValue":35.6}]}]}]},
                 {"startTime":1788548400000,"endTime":1788634799999,"sampleSet":[{"samplePoints":[
                   {"dataTypeName":"com.huawei.continuous.steps.total","value":[{"fieldName":"steps","integerValue":0}]}]}]}]}""",
        ).group
        val samples = groups.flatMap { HuaweiMapper.fromDay(it, tashkent) }
        assertEquals(
            mapOf("steps" to 9000.0, "heart_rate_average" to 72.5, "weight_kg" to 61.4, "body_fat_rate" to 24.5, "skin_temperature" to 35.6),
            samples.associate { it.metric to it.value },
        )
        assertTrue(samples.all { it.externalId.startsWith("day:2026-09-04:") && it.startedAt == Instant.parse("2026-09-04T12:00:00Z") })
    }

    @Test
    fun `a night lands on the morning she woke, and a nap is not the night`() {
        val records = json.decodeFromString<HuaweiHealthRecords>(
            """{"healthRecords":[
                 {"startTime":1788458400000000000,"endTime":1788485400000000000,"dataTypeName":"com.huawei.health.record.sleep","value":[
                   {"fieldName":"fall_asleep_time","longValue":1788458400000},{"fieldName":"wakeup_time","longValue":1788485400000},
                   {"fieldName":"all_sleep_time","integerValue":430},{"fieldName":"deep_sleep_time","integerValue":95},
                   {"fieldName":"dream_time","integerValue":100},{"fieldName":"light_sleep_time","integerValue":235},
                   {"fieldName":"awake_time","integerValue":20},{"fieldName":"sleep_score","integerValue":84},{"fieldName":"sleep_type","integerValue":1}]},
                 {"startTime":1788516000000000000,"endTime":1788519600000000000,"dataTypeName":"com.huawei.health.record.sleep","value":[
                   {"fieldName":"fall_asleep_time","longValue":1788516000000},{"fieldName":"wakeup_time","longValue":1788519600000},
                   {"fieldName":"all_sleep_time","integerValue":60},{"fieldName":"sleep_type","integerValue":3}]}]}""",
        ).healthRecords
        val samples = records.flatMap { HuaweiMapper.fromSleep(it, tashkent) }
        assertEquals(
            mapOf("minutes_asleep" to 430.0, "minutes_deep" to 95.0, "minutes_rem" to 100.0, "minutes_light" to 235.0,
                "minutes_awake" to 20.0, "sleep_score" to 84.0),
            samples.associate { it.metric to it.value },
        )
        // She fell asleep at 23:00 on the 3rd and woke at 06:30 on the 4th, Tashkent time.
        assertTrue(samples.all { it.externalId.startsWith("sleep:2026-09-04:") })
        assertEquals(Instant.fromEpochMilliseconds(1788485400000), samples.first().startedAt)
        assertEquals(Instant.fromEpochMilliseconds(1788458400000), samples.first().endedAt)
    }

    @Test
    fun `the night's HRV samples become the day's average`() {
        val points = json.decodeFromString<HuaweiGroups>(
            """{"group":[{"startTime":1788375600000,"endTime":1788516000000,"sampleSet":[{"samplePoints":[
                 {"startTime":1788466000000000000,"endTime":1788466000000000000,"dataTypeName":"com.huawei.heart_rate_variability",
                  "value":[{"fieldName":"heartRateVariabilityRMSSD","integerValue":40}]},
                 {"startTime":1788476000000000000,"endTime":1788476000000000000,"dataTypeName":"com.huawei.heart_rate_variability",
                  "value":[{"fieldName":"heartRateVariabilityRMSSD","integerValue":50}]}]}]}]}""",
        ).group.flatMap { group -> group.sampleSet.flatMap { it.samplePoints } }
        val sample = HuaweiMapper.fromHrv(points, tashkent).single()
        assertEquals("day:2026-09-04:hrv_rmssd", sample.externalId)
        assertEquals(45.0, sample.value)
    }

    @Test
    fun `a notification is believed only with Huawei's signature over who, what and when`() {
        val secret = Base64.getEncoder().encodeToString("subscriber-secret".toByteArray())
        val body = """{"eventTime":1607514090688,"eventType":"SAMPLESET_EVENT${'$'}UPDATE${'$'}com.huawei.continuous.steps.total",
            "type":"SAMPLESET_EVENT","subType":"UPDATE","metaData":[{"metaKey":"startTime","metaValue":"1"}],"subscriptionId":"s","openId":"open-1"}"""
        val notification = assertNotNull(HuaweiNotification.parse(body))
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("subscriber-secret".toByteArray(), "HmacSHA256")) }
        val signature = Base64.getEncoder().encodeToString(
            mac.doFinal("open-1_SAMPLESET_EVENT${'$'}UPDATE${'$'}com.huawei.continuous.steps.total_1607514090688".toByteArray()),
        )
        assertTrue(notification.signedWith(secret, signature))
        assertFalse(notification.signedWith(secret, signature.reversed()))
        assertFalse(notification.copy(openId = "someone-else").signedWith(secret, signature))
        assertTrue(notification.isUpdate && !notification.isRevocation)
        assertTrue(assertNotNull(HuaweiNotification.parse("""{"eventType":"USER_EVENT${'$'}REVOKE_ALL","type":"USER_EVENT","subType":"REVOKE_ALL"}""")).isRevocation)
        assertNull(HuaweiNotification.parse("""{"dailies":[]}"""))
    }
}
