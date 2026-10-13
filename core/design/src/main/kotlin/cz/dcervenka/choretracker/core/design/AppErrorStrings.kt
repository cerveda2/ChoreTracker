package cz.dcervenka.choretracker.core.design

import androidx.annotation.StringRes
import cz.dcervenka.choretracker.core.model.error.AppError

@Suppress("CyclomaticComplexMethod")
@StringRes
fun AppError.toStringRes(): Int = when (this) {
    AppError.EMAIL_REQUIRED -> R.string.auth_validation_email_required
    AppError.PASSWORD_REQUIRED -> R.string.auth_validation_password_required
    AppError.DISPLAY_NAME_REQUIRED -> R.string.auth_validation_display_name_required
    AppError.INVALID_FREQUENCY -> R.string.error_invalid_frequency
    AppError.PARTICIPANT_REQUIRED -> R.string.error_participant_required
    AppError.FIREBASE_NOT_CONFIGURED -> R.string.error_firebase_not_configured
    AppError.NOT_SIGNED_IN -> R.string.error_not_signed_in
    AppError.PREVIEW_MODE_RESTRICTED -> R.string.error_preview_mode_restricted
    AppError.INVITE_NOT_FOUND -> R.string.error_invite_not_found
    AppError.INVITE_HOUSEHOLD_UNAVAILABLE -> R.string.error_invite_household_unavailable
    AppError.HOUSEHOLD_NOT_FOUND -> R.string.error_household_not_found
    AppError.MEMBER_NOT_FOUND -> R.string.error_member_not_found
    AppError.OWN_MEMBERSHIP_NOT_FOUND -> R.string.error_own_membership_not_found
    AppError.COMPLETION_NOT_FOUND -> R.string.error_completion_not_found
    AppError.COMPLETION_EDIT_FORBIDDEN -> R.string.error_completion_edit_forbidden
    AppError.COMPLETION_DELETE_FORBIDDEN -> R.string.error_completion_delete_forbidden
    AppError.NOT_HOUSEHOLD_OWNER -> R.string.error_not_household_owner
    AppError.MEMBER_NOT_JOINED -> R.string.error_member_not_joined
    AppError.ALREADY_OWNER -> R.string.error_already_owner
    AppError.OWNER_CANNOT_LEAVE -> R.string.error_owner_cannot_leave
    AppError.SYNC_PARTIALLY_FAILED -> R.string.error_sync_partially_failed
    AppError.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
    AppError.INVALID_EMAIL -> R.string.error_invalid_email
    AppError.EMAIL_ALREADY_IN_USE -> R.string.error_email_already_in_use
    AppError.WEAK_PASSWORD -> R.string.error_weak_password
    AppError.TOO_MANY_REQUESTS -> R.string.error_too_many_requests
    AppError.NETWORK -> R.string.error_network
    AppError.PERMISSION_DENIED -> R.string.error_permission_denied
    AppError.UNKNOWN -> R.string.error_unknown
}
