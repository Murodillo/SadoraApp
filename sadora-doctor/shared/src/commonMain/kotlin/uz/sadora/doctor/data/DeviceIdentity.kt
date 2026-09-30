package uz.sadora.doctor.data

import uz.sadora.contract.ClientApp
import uz.sadora.contract.DeviceInfo
import uz.sadora.contract.Platform

/** What the app tells the backend about the phone it is running on. */
interface DeviceIdentity {
    val platform: Platform
    val osVersion: String
    val model: String
    val timezone: String
    suspend fun installationId(): String
}

/**
 * Always as the doctor app: a doctor has the women's app on the same phone too, and the
 * server sends a patient's message to the devices registered with [ClientApp.DOCTOR].
 */
suspend fun DeviceIdentity.toDeviceInfo(
    appVersion: String? = null,
    pushToken: String? = null,
): DeviceInfo = DeviceInfo(
    deviceId = installationId(),
    platform = platform,
    osVersion = osVersion,
    appVersion = appVersion,
    model = model,
    pushToken = pushToken,
    timezone = timezone,
    app = ClientApp.DOCTOR,
)

/** Fixed values for tests and previews. */
class FixedDeviceIdentity(
    override val platform: Platform = Platform.ANDROID,
    override val osVersion: String = "test",
    override val model: String = "test",
    override val timezone: String = "Asia/Tashkent",
    private val id: String = "test-device",
) : DeviceIdentity {
    override suspend fun installationId(): String = id
}
