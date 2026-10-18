package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import uz.sadora.app.model.AppState
import uz.sadora.contract.DailyHealth
import uz.sadora.contract.DailyMetric
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.HealthProvider

class DeviceProfilesTest {

    private fun day(vararg metrics: Triple<HealthMetric, Double, HealthProvider>) = DailyHealth(
        LocalDate.parse("2026-10-16"),
        metrics.map { (metric, value, provider) -> DailyMetric(metric, value, metric.canonicalUnit, 1, listOf(provider)) },
    )

    @Test
    fun `the wearable is the primary device even when the phone sent more`() {
        val today = day(
            Triple(HealthMetric.STEPS, 9000.0, HealthProvider.HEALTH_CONNECT),
            Triple(HealthMetric.HRV, 40.0, HealthProvider.HEALTH_CONNECT),
            Triple(HealthMetric.RECOVERY, 71.0, HealthProvider.WHOOP),
        )
        assertEquals(HealthProvider.WHOOP, DeviceProfiles.primary(today))
    }

    @Test
    fun `a whoop day leads with recovery and strain — a garmin day with its own signals`() {
        val whoop = day(
            Triple(HealthMetric.HRV, 55.0, HealthProvider.WHOOP),
            Triple(HealthMetric.STRAIN, 9.4, HealthProvider.WHOOP),
            Triple(HealthMetric.RECOVERY, 71.0, HealthProvider.WHOOP),
        )
        assertEquals(listOf(HealthMetric.RECOVERY, HealthMetric.STRAIN, HealthMetric.HRV), DeviceProfiles.bodySignals(whoop))

        val garmin = day(
            Triple(HealthMetric.VO2_MAX, 38.0, HealthProvider.GARMIN),
            Triple(HealthMetric.SPO2, 96.0, HealthProvider.GARMIN),
            Triple(HealthMetric.RESTING_HEART_RATE, 58.0, HealthProvider.GARMIN),
        )
        assertEquals(
            listOf(HealthMetric.RESTING_HEART_RATE, HealthMetric.SPO2, HealthMetric.VO2_MAX),
            DeviceProfiles.bodySignals(garmin),
        )
    }

    @Test
    fun `a garmin sleep score is a score and a whoop performance is a percentage`() {
        assertEquals("82", DeviceProfiles.format(HealthMetric.SLEEP_PERFORMANCE, 82.0, HealthProvider.GARMIN))
        assertEquals("82%", DeviceProfiles.format(HealthMetric.SLEEP_PERFORMANCE, 82.0, HealthProvider.WHOOP))
    }

    @Test
    fun `oura's night is scored by its efficiency when there is no performance`() {
        val oura = day(Triple(HealthMetric.SLEEP_EFFICIENCY, 88.0, HealthProvider.OURA))
        assertEquals(HealthMetric.SLEEP_EFFICIENCY, DeviceProfiles.sleepScore(oura))
    }

    @Test
    fun `a whoop owner's activity is her strain even when the phone counts steps`() {
        val state = AppState()
        state.applyWearables(
            day(
                Triple(HealthMetric.STEPS, 9000.0, HealthProvider.HEALTH_CONNECT),
                Triple(HealthMetric.STRAIN, 10.5, HealthProvider.WHOOP),
            ),
        )
        assertTrue(state.showsStrain)

        state.applyWearables(
            day(
                Triple(HealthMetric.STEPS, 9000.0, HealthProvider.GARMIN),
                Triple(HealthMetric.HRV, 40.0, HealthProvider.GARMIN),
            ),
        )
        assertFalse(state.showsStrain)
    }
}
