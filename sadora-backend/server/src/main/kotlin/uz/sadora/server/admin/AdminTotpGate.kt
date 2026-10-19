package uz.sadora.server.admin

import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.plugins.AdminPrincipal

/** Answered to an operator who must enrol 2FA before anything but her own account page. */
const val TOTP_SETUP_REQUIRED = "totp_setup_required"

/** Answered to an operator still on the temporary password the Owner gave her. */
const val PASSWORD_CHANGE_REQUIRED = "password_change_required"

/**
 * Refuses every admin route but the operator's own account page until she has enrolled
 * 2FA where it is mandatory. Kept apart from the role check so no route can forget it:
 * every guarded route goes through `requireAdminRole`, which calls this first.
 */
fun requireTotpEnrolled(principal: AdminPrincipal) {
    if (principal.totpSetupRequired) {
        throw ForbiddenException(TOTP_SETUP_REQUIRED, "Avval 2FA ni yoqing")
    }
}

/**
 * The same gate for a temporary password: the Owner has seen it, so nothing but her own
 * account page opens until she replaces it with one only she knows.
 */
fun requirePasswordChanged(principal: AdminPrincipal) {
    if (principal.passwordChangeRequired) {
        throw ForbiddenException(PASSWORD_CHANGE_REQUIRED, "Avval vaqtinchalik parolni almashtiring")
    }
}
