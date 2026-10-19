package uz.sadora.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Whether this install should update: answered by the server from the admin panel's release policy. */
@Serializable
enum class AppUpdate {
    @SerialName("none") NONE,
    /** A newer build is out; the app says so once and lets her carry on. */
    @SerialName("suggested") SUGGESTED,
    /** This build is below the minimum: the app shows only the update screen. */
    @SerialName("required") REQUIRED,
}

/**
 * Asked before sign-in, on every start and every return to the app. [build] is the store's
 * build number — Android's versionCode, iOS's CFBundleVersion — because the visible version
 * stays "1.0" on iOS while only the build grows.
 */
@Serializable
data class AppStatusRequest(
    val platform: Platform,
    val build: Int? = null,
)

@Serializable
data class AppStatus(
    val update: AppUpdate = AppUpdate.NONE,
    /** Where the update button leads; null when the store page is not set. */
    val storeUrl: String? = null,
    /** Closed for maintenance: the app shows [message] and a retry instead of itself. */
    val maintenance: Boolean = false,
    /** In the language the request asked for, falling back to Uzbek. */
    val message: String? = null,
)
