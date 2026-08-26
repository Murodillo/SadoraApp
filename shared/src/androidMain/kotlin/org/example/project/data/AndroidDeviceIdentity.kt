package org.example.project.data

import android.content.Context
import android.os.Build
import java.util.TimeZone
import uz.sadora.contract.Platform

class AndroidDeviceIdentity(context: Context) : DeviceIdentity {

    private val storage = AndroidTokenStorage(context)

    override val platform: Platform = Platform.ANDROID
    override val osVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    override val model: String = "${Build.MANUFACTURER} ${Build.MODEL}"
    override val timezone: String get() = TimeZone.getDefault().id

    override suspend fun installationId(): String = storage.installationId()
}
