package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AuthProvider {
    @SerialName("phone") PHONE,
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
    /** Which app this is: a doctor has both on one phone, and a push must ring in the right one. */
    val app: ClientApp = ClientApp.CLIENT,
    /** What this build can do that older ones cannot; see [DeviceFeatures]. */
    val features: List<String> = emptyList(),
)

/** Capabilities a client build reports, so the server never sends an older one what it cannot draw. */
object DeviceFeatures {
    /** Draws a medication reminder itself, with "taken" and "skipped" (Android since the widgets). */
    const val DOSE_ACTIONS = "dose_actions"
}

@Serializable
enum class ClientApp {
    /** The women's app. */
    @SerialName("client") CLIENT,

    /** Sadora Doctor. */
    @SerialName("doctor") DOCTOR,
}

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

// Users have no password. Signing in is the phone code exchange, which returns the
// existing account or creates one — so there is no separate registration request, and
// nothing for someone who signed up by phone to have forgotten. The admin panel's own
// credentials live in its own request type.

// ---- tokens ----

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(
    val refreshToken: String? = null,
    /** Sign out every device, not just this one. */
    val allDevices: Boolean = false,
)

/** Who a companion session is for. */
@Serializable
enum class CompanionKind {
    /** The iOS widget extension: a process of its own that cannot share the app's tokens. */
    @SerialName("widget") WIDGET,
    /** The Apple Watch or Wear OS app, which may reach the server without the phone. */
    @SerialName("watch") WATCH,
}

/**
 * Asks for a session for the widget extension or the watch, made from the app's own.
 *
 * Refresh tokens rotate and a spent one presented again ends the session, so two
 * processes cannot share one: whichever refreshed second would look like a thief. The
 * companion gets a family of its own, tied to the app's — signing the app out ends it.
 * [refreshToken] is the app's current one; it is read, not spent.
 */
@Serializable
data class CompanionSessionRequest(
    val refreshToken: String,
    val kind: CompanionKind,
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
