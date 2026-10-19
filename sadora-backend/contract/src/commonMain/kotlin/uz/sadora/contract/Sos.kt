package uz.sadora.contract

import kotlinx.serialization.Serializable

/**
 * SOS: a call to 103 on the phone itself, and an alert — SMS to up to three people she chose
 * and to Yaqinim, with her location once as a map link. Free for everyone. It does not replace
 * the emergency services and never says it does.
 */
@Serializable
data class SosContact(val name: String, val phone: String)

@Serializable
data class SosState(
    val contacts: List<SosContact> = emptyList(),
    /** Her Yaqinim's name when one is linked: they are alerted too. */
    val partnerName: String? = null,
)

@Serializable
data class SaveSosContactsRequest(val contacts: List<SosContact>)

@Serializable
data class SosAlertRequest(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Int? = null,
)

@Serializable
data class SosAlertResult(
    /** People the SMS reached the gateway for. */
    val smsSent: Int = 0,
    val smsFailed: Int = 0,
    val partnerNotified: Boolean = false,
    val withLocation: Boolean = false,
)
