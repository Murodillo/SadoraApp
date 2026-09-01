package uz.sadora.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every route in the API lives under this prefix. */
const val API_VERSION: String = "v1"

/** UI language. Matches `AppLanguage` on the mobile side. */
@Serializable
enum class Language {
    @SerialName("uz") UZ,
    @SerialName("ru") RU,
    @SerialName("en") EN,
}

/**
 * The life stage chosen during onboarding — the single biggest branch in the product.
 * The server uses it to decide whether cycle prediction runs at all.
 */
@Serializable
enum class LifeStage {
    @SerialName("cycle") CYCLE,
    @SerialName("trying_to_conceive") TRYING_TO_CONCEIVE,
    @SerialName("pregnancy") PREGNANCY,
    @SerialName("postpartum") POSTPARTUM,
    @SerialName("perimenopause") PERIMENOPAUSE,
    @SerialName("menopause") MENOPAUSE;

    /** Stages that never receive a cycle-day prediction. */
    val predictsCycle: Boolean
        get() = this == CYCLE || this == TRYING_TO_CONCEIVE
}

/** The eight onboarding goals. Selected goals surface first on Today. */
@Serializable
enum class Goal {
    @SerialName("understand_cycle") UNDERSTAND_CYCLE,
    @SerialName("sleep_better") SLEEP_BETTER,
    @SerialName("more_energy") MORE_ENERGY,
    @SerialName("less_stress") LESS_STRESS,
    @SerialName("eat_balanced") EAT_BALANCED,
    @SerialName("drink_water") DRINK_WATER,
    @SerialName("be_active") BE_ACTIVE,
    @SerialName("remember_meds") REMEMBER_MEDS,
}

@Serializable
enum class SubscriptionTier {
    @SerialName("free") FREE,
    @SerialName("premium") PREMIUM,
}

/** Where an entitlement came from. Entitlement logic is identical for all of them. */
@Serializable
enum class SubscriptionSource {
    @SerialName("app_store") APP_STORE,
    @SerialName("google_play") GOOGLE_PLAY,
    @SerialName("payme") PAYME,
    @SerialName("click") CLICK,
    @SerialName("manual") MANUAL,
}

@Serializable
enum class Platform {
    @SerialName("ios") IOS,
    @SerialName("android") ANDROID,
    @SerialName("web") WEB,
}

/** Errors are always this shape, whatever went wrong. */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val requestId: String? = null,
)

@Serializable
data class ApiErrorResponse(val error: ApiError)

/** Stable error codes. Clients branch on these, never on the message text. */
object ErrorCodes {
    const val VALIDATION_FAILED = "validation_failed"
    const val UNAUTHORIZED = "unauthorized"
    const val FORBIDDEN = "forbidden"
    const val NOT_FOUND = "not_found"
    const val CONFLICT = "conflict"
    const val RATE_LIMITED = "rate_limited"
    const val OTP_INVALID = "otp_invalid"
    const val OTP_EXPIRED = "otp_expired"
    const val OTP_TOO_MANY_ATTEMPTS = "otp_too_many_attempts"
    const val TOKEN_EXPIRED = "token_expired"
    const val TOKEN_REVOKED = "token_revoked"
    const val SOCIAL_TOKEN_INVALID = "social_token_invalid"
    const val ACCOUNT_BLOCKED = "account_blocked"
    const val ENTITLEMENT_REQUIRED = "entitlement_required"

    /** Health data was sent without the consent that permits storing it. */
    const val CONSENT_REQUIRED = "consent_required"
    const val LIMIT_REACHED = "limit_reached"
    const val FEATURE_DISABLED = "feature_disabled"
    const val INTERNAL_ERROR = "internal_error"
}

@Serializable
data class Page<T>(
    val items: List<T>,
    val total: Long,
    val limit: Int,
    val offset: Int,
) {
    val hasMore: Boolean get() = offset + items.size < total
}

/** Returned by endpoints whose only job is to succeed. */
@Serializable
data class Ack(val ok: Boolean = true)
