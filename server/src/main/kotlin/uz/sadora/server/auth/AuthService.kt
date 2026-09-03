package uz.sadora.server.auth

import kotlin.uuid.Uuid
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.AuthProvider
import uz.sadora.contract.AuthSession
import uz.sadora.contract.DeviceInfo
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.Language
import uz.sadora.contract.OtpVerifyRequest
import uz.sadora.contract.SocialSignInRequest
import uz.sadora.contract.TokenPair
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.core.DEFAULT_TIMEZONE
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.UnauthorizedException
import uz.sadora.server.core.isValidTimeZone
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.user.NewUser
import uz.sadora.server.user.UserRecord
import uz.sadora.server.user.UserRepository

/** Request metadata that only exists to make the audit trail useful. */
data class RequestContext(val ip: String?, val userAgent: String?)

class AuthService(
    private val users: UserRepository,
    private val otp: OtpService,
    private val social: SocialVerifier,
    private val refreshTokens: RefreshTokenService,
    private val jwt: JwtService,
    private val entitlements: EntitlementService,
    private val audit: AuditService,
) {

    // ---------------------------------------------------------------- phone

    suspend fun signInWithOtp(request: OtpVerifyRequest, context: RequestContext): AuthSession {
        val phone = otp.verify(request.challengeId, request.code)
        val existing = users.findByPhone(phone)
        val isNewUser = existing == null
        val user = existing ?: users.create(
            NewUser(phone = phone, timezone = request.device.timezone.orDefaultTimeZone()),
        )
        return completeSignIn(user, request.device, isNewUser, AuthProvider.PHONE, context)
    }

    // ---------------------------------------------------------------- social

    suspend fun signInWithSocial(request: SocialSignInRequest, context: RequestContext): AuthSession {
        val identity = social.verify(request.provider, request.idToken)

        val linked = users.findByProviderSubject(identity.provider, identity.subject)
        if (linked != null) {
            return completeSignIn(linked, request.device, false, identity.provider, context)
        }

        // A verified address from the provider is enough to attach to an existing
        // account; an unverified one is not, or anyone could claim someone else's inbox.
        val byEmail = identity.email
            ?.takeIf { identity.emailVerified }
            ?.let { users.findByEmail(it) }

        if (byEmail != null) {
            users.linkIdentity(byEmail.id, identity.provider, identity.subject, identity.email)
            return completeSignIn(byEmail, request.device, false, identity.provider, context)
        }

        val created = users.create(
            NewUser(
                email = identity.email,
                // Apple only ever sends the name once, so take whichever we were given.
                name = request.fullName ?: identity.name.orEmpty(),
                timezone = request.device.timezone.orDefaultTimeZone(),
            ),
        )
        users.linkIdentity(created.id, identity.provider, identity.subject, identity.email)
        return completeSignIn(created, request.device, true, identity.provider, context)
    }

    // Email and password are deliberately absent for users. Sign-in is the phone code
    // exchange, so an account opened with a password could never be signed back into —
    // and a credential path no client uses is one nobody is watching. The admin realm
    // keeps its own, behind 2FA, in `admin_users`.

    // ---------------------------------------------------------------- tokens

    suspend fun refresh(rawRefreshToken: String, deviceId: String?): AuthSession {
        val (userId, issued) = refreshTokens.rotate(rawRefreshToken, deviceId)
        val user = users.findById(userId)
            ?: throw UnauthorizedException(message = "Foydalanuvchi topilmadi")
        requireActive(user)

        val access = jwt.issueAccessToken(user.id, TokenSubjectType.USER)
        return AuthSession(
            tokens = TokenPair(
                accessToken = access.value,
                refreshToken = issued.value,
                accessExpiresAt = access.expiresAt,
                refreshExpiresAt = issued.expiresAt,
            ),
            user = user.toProfile(users.goalsOf(user.id)),
            entitlements = entitlements.resolve(user.id, user.timezone),
            isNewUser = false,
        )
    }

    suspend fun logout(
        userId: Uuid,
        rawRefreshToken: String?,
        allDevices: Boolean,
        context: RequestContext,
    ) {
        if (allDevices) {
            refreshTokens.revokeAllForUser(userId, "logout_all")
        } else {
            rawRefreshToken?.let { refreshTokens.revoke(it) }
        }
        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = userId,
                action = AuditActions.USER_SIGNED_OUT,
                entityType = "user",
                entityId = userId.toString(),
                metadata = mapOf("allDevices" to allDevices.toString()),
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
    }

    // ---------------------------------------------------------------- shared

    private suspend fun completeSignIn(
        user: UserRecord,
        device: DeviceInfo,
        isNewUser: Boolean,
        provider: AuthProvider,
        context: RequestContext,
    ): AuthSession {
        requireActive(user)
        users.registerDevice(user.id, device)
        users.touchLastActive(user.id)

        val access = jwt.issueAccessToken(user.id, TokenSubjectType.USER)
        val refresh = refreshTokens.issue(user.id, device.deviceId)

        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = user.id,
                action = if (isNewUser) AuditActions.USER_SIGNED_UP else AuditActions.USER_SIGNED_IN,
                entityType = "user",
                entityId = user.id.toString(),
                metadata = mapOf(
                    "provider" to provider.name.lowercase(),
                    "platform" to device.platform.name.lowercase(),
                    "deviceId" to device.deviceId,
                ),
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )

        return AuthSession(
            tokens = TokenPair(
                accessToken = access.value,
                refreshToken = refresh.value,
                accessExpiresAt = access.expiresAt,
                refreshExpiresAt = refresh.expiresAt,
            ),
            user = user.toProfile(users.goalsOf(user.id)),
            entitlements = entitlements.resolve(user.id, user.timezone),
            isNewUser = isNewUser,
        )
    }

    private fun requireActive(user: UserRecord) {
        when (user.status) {
            AccountStatus.ACTIVE -> Unit
            AccountStatus.BLOCKED -> throw ForbiddenException(
                ErrorCodes.ACCOUNT_BLOCKED,
                user.blockedReason ?: "Hisob bloklangan",
            )

            AccountStatus.DELETION_PENDING -> throw ForbiddenException(
                ErrorCodes.ACCOUNT_BLOCKED,
                "Hisobni o'chirish so'rovi yuborilgan",
            )
        }
    }

    private fun String?.orDefaultTimeZone(): String =
        this?.takeIf { isValidTimeZone(it) } ?: DEFAULT_TIMEZONE

}

/** Kept out of [AuthService] so `Language` stays a contract type in one place only. */
internal fun Language.orDefault(): Language = this
