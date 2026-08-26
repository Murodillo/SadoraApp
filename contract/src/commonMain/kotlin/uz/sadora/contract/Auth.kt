package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AuthProvider {
    @SerialName("phone") PHONE,
    @SerialName("email") EMAIL,
    @SerialName("apple") APPLE,
    @SerialName("google") GOOGLE,
}

/**
 * Device metadata sent with every sign-in. It lands on the session row so support can
 * answer "which phone is this?" without ever touching health data.
 */
@Serializable
data class DeviceInfo(
    val deviceId: String,
    val platform: Platform,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val model: String? = null,
    val pushToken: String? = null,
    val timezone: String? = null,
)

// ---- phone OTP ----

@Serializable
data class OtpRequest(
    val phone: String,
    val language: Language = Language.UZ,
)

/**
 * The verify step quotes [challengeId] back rather than the phone number, so the code
 * can only be spent on the challenge it was issued for.
 *
 * [devCode] is populated only when the server runs with `auth.otp.exposeCode = true`,
 * which is refused outside dev and stage.
 */
@Serializable
data class OtpChallenge(
    val challengeId: String,
    val expiresAt: Instant,
    val resendAfterSeconds: Int,
    val attemptsLeft: Int,
    val devCode: String? = null,
)

@Serializable
data class OtpVerifyRequest(
    val challengeId: String,
    val code: String,
    val device: DeviceInfo,
)

// ---- social ----

@Serializable
data class SocialSignInRequest(
    /** `apple` or `google`. */
    val provider: AuthProvider,
    /** The provider's OIDC identity token, verified server-side against their JWKS. */
    val idToken: String,
    /**
     * Apple hands the display name over exactly once, on first authorisation. The client
     * forwards it here because it cannot be recovered later.
     */
    val fullName: String? = null,
    val device: DeviceInfo,
)

// ---- email + password (admin panel, and optional for users) ----

@Serializable
data class EmailSignInRequest(
    val email: String,
    val password: String,
    val device: DeviceInfo? = null,
)

@Serializable
data class EmailRegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val language: Language = Language.UZ,
    val device: DeviceInfo,
)

// ---- tokens ----

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(
    val refreshToken: String? = null,
    /** Sign out every device, not just this one. */
    val allDevices: Boolean = false,
)

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Instant,
    val refreshExpiresAt: Instant,
    val tokenType: String = "Bearer",
)

/**
 * What every successful sign-in returns. The profile and entitlement snapshot ride along
 * so the client can render Today without a second round trip.
 */
@Serializable
data class AuthSession(
    val tokens: TokenPair,
    val user: UserProfile,
    val entitlements: Entitlements,
    /** True when this call created the account — the client then routes to onboarding. */
    val isNewUser: Boolean,
)
