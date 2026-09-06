package org.example.project.data

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
