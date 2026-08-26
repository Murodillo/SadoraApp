package org.example.project.data

import org.example.project.model.AppLanguage
import org.example.project.model.AppState
import org.example.project.model.Goal
import org.example.project.model.LifeStage
import uz.sadora.contract.ConsentGrants
import uz.sadora.contract.Consents
import uz.sadora.contract.CycleBaseline
import uz.sadora.contract.Entitlements
import uz.sadora.contract.OnboardingRequest
import uz.sadora.contract.PermissionGrants
import uz.sadora.contract.UpdateProfileRequest
import uz.sadora.contract.SubscriptionTier
import uz.sadora.contract.UserProfile
import uz.sadora.contract.Goal as WireGoal
import uz.sadora.contract.Language as WireLanguage
import uz.sadora.contract.LifeStage as WireLifeStage

/**
 * Copies a server profile onto the in-memory store the screens read from.
 *
 * The wire enums and the UI enums are kept separate on purpose: the UI ones carry Uzbek
 * labels and palettes, the wire ones carry the spelling the backend stores. Mapping them
 * here means renaming a label never changes what goes over the network.
 *
 * A value the app does not recognise is left at whatever it already was rather than
 * guessed at — a server that gains a seventh life stage should not silently move a user
 * to a stage the app cannot render.
 */
fun AppState.applyServerProfile(profile: UserProfile, entitlements: Entitlements) {
    name = profile.name
    email = profile.email.orEmpty()
    phone = profile.phone.orEmpty()
    profile.birthDate?.let { birthDate = it.toDisplayDate() }
    profile.heightCm?.let { heightCm = it.toString() }
    profile.weightKg?.let { weightKg = it.toString() }
    profile.language.toAppLanguage()?.let { language = it }
    profile.lifeStage.toAppLifeStage()?.let { lifeStage = it }

    goals.clear()
    goals.addAll(profile.goals.mapNotNull { it.toAppGoal() })

    isPremium = entitlements.tier == SubscriptionTier.PREMIUM
}

private fun WireLanguage.toAppLanguage(): AppLanguage? = when (this) {
    WireLanguage.UZ -> AppLanguage.Uz
    WireLanguage.RU -> AppLanguage.Ru
    WireLanguage.EN -> AppLanguage.En
}

private fun WireLifeStage.toAppLifeStage(): LifeStage? = when (this) {
    WireLifeStage.CYCLE -> LifeStage.Cycle
    WireLifeStage.TRYING_TO_CONCEIVE -> LifeStage.TryingToConceive
    WireLifeStage.PREGNANCY -> LifeStage.Pregnancy
    WireLifeStage.POSTPARTUM -> LifeStage.Postpartum
    WireLifeStage.PERIMENOPAUSE -> LifeStage.Perimenopause
    WireLifeStage.MENOPAUSE -> LifeStage.Menopause
}

private fun WireGoal.toAppGoal(): Goal? = when (this) {
    WireGoal.UNDERSTAND_CYCLE -> Goal.UnderstandCycle
    WireGoal.SLEEP_BETTER -> Goal.SleepBetter
    WireGoal.MORE_ENERGY -> Goal.MoreEnergy
    WireGoal.LESS_STRESS -> Goal.LessStress
    WireGoal.EAT_BALANCED -> Goal.EatBalanced
    WireGoal.DRINK_WATER -> Goal.DrinkWater
    WireGoal.BE_ACTIVE -> Goal.BeActive
    WireGoal.REMEMBER_MEDS -> Goal.RememberMeds
}

/** The app writes dates as `14.03.1994`; the wire format is ISO. */
private fun kotlinx.datetime.LocalDate.toDisplayDate(): String =
    "${day.pad()}.${monthNumber.pad()}.$year"

private fun Int.pad(): String = toString().padStart(2, '0')

// ---------------------------------------------------------------- app -> wire

/**
 * The whole onboarding flow as one request.
 *
 * Sent as a unit deliberately: a user who drops out halfway leaves no half-built
 * profile on the server, which is also why the app keeps her in the flow until this
 * call succeeds.
 */
fun AppState.toOnboardingRequest(timezone: String): OnboardingRequest = OnboardingRequest(
    name = name,
    language = language.toWire(),
    timezone = timezone,
    lifeStage = lifeStage.toWire(),
    birthDate = birthDate.toWireDate(),
    heightCm = heightCm.toIntOrNull(),
    weightKg = weightKg.toIntOrNull(),
    goals = goals.map { it.toWire() },
    // The server ignores the cycle baseline for stages that do not predict one.
    cycle = if (lifeStage.predictsCycle) {
        CycleBaseline(
            averageCycleLength = averageCycleLength,
            averagePeriodLength = averagePeriodLength,
        )
    } else {
        null
    },
    permissions = toPermissionGrants(),
    consents = toConsentGrants(),
)

/** A partial update carrying only the fields the profile screens can edit. */
fun AppState.toUpdateProfileRequest(timezone: String? = null): UpdateProfileRequest =
    UpdateProfileRequest(
        name = name,
        language = language.toWire(),
        timezone = timezone,
        lifeStage = lifeStage.toWire(),
        birthDate = birthDate.toWireDate(),
        heightCm = heightCm.toIntOrNull(),
        weightKg = weightKg.toIntOrNull(),
        goals = goals.map { it.toWire() },
    )

fun AppState.toConsentGrants(): ConsentGrants = ConsentGrants(
    storeHealth = consentStoreHealth,
    aiInsights = consentAiInsights,
    analytics = consentAnalytics,
    marketing = false,
)

fun AppState.toPermissionGrants(): PermissionGrants = PermissionGrants(
    notifications = notificationsAllowed,
    healthData = healthDataAllowed,
    camera = cameraAllowed,
)

/** Copies server consents back onto the store, so Privacy shows what is actually set. */
fun AppState.applyServerConsents(consents: Consents) {
    consentStoreHealth = consents.storeHealth
    consentAiInsights = consents.aiInsights
    consentAnalytics = consents.analytics
}

private fun AppLanguage.toWire(): WireLanguage = when (this) {
    AppLanguage.Uz -> WireLanguage.UZ
    AppLanguage.Ru -> WireLanguage.RU
    AppLanguage.En -> WireLanguage.EN
}

private fun LifeStage.toWire(): WireLifeStage = when (this) {
    LifeStage.Cycle -> WireLifeStage.CYCLE
    LifeStage.TryingToConceive -> WireLifeStage.TRYING_TO_CONCEIVE
    LifeStage.Pregnancy -> WireLifeStage.PREGNANCY
    LifeStage.Postpartum -> WireLifeStage.POSTPARTUM
    LifeStage.Perimenopause -> WireLifeStage.PERIMENOPAUSE
    LifeStage.Menopause -> WireLifeStage.MENOPAUSE
}

private fun Goal.toWire(): WireGoal = when (this) {
    Goal.UnderstandCycle -> WireGoal.UNDERSTAND_CYCLE
    Goal.SleepBetter -> WireGoal.SLEEP_BETTER
    Goal.MoreEnergy -> WireGoal.MORE_ENERGY
    Goal.LessStress -> WireGoal.LESS_STRESS
    Goal.EatBalanced -> WireGoal.EAT_BALANCED
    Goal.DrinkWater -> WireGoal.DRINK_WATER
    Goal.BeActive -> WireGoal.BE_ACTIVE
    Goal.RememberMeds -> WireGoal.REMEMBER_MEDS
}

/**
 * Parses the `14.03.1994` the personal-details field produces.
 *
 * Returns null rather than throwing on anything unparseable — the field is free text and
 * optional, and a half-typed date must not take down the request that carries it.
 */
internal fun String.toWireDate(): kotlinx.datetime.LocalDate? {
    val parts = trim().split('.')
    if (parts.size != 3) return null
    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val year = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31 || year !in 1900..2100) return null
    return runCatching { kotlinx.datetime.LocalDate(year, month, day) }.getOrNull()
}
