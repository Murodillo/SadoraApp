package org.example.project.data

import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.UIKit.UIDevice
import uz.sadora.contract.Platform

class IosDeviceIdentity(
    private val storage: TokenStorage = KeychainTokenStorage(),
) : DeviceIdentity {

    override val platform: Platform = Platform.IOS
    override val osVersion: String = "iOS ${UIDevice.currentDevice.systemVersion}"
    override val model: String = UIDevice.currentDevice.model
    override val timezone: String get() = NSTimeZone.localTimeZone.name

    override suspend fun installationId(): String = storage.installationId()
}
