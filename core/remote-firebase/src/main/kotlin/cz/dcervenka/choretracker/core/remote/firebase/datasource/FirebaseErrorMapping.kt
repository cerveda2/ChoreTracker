package cz.dcervenka.choretracker.core.remote.firebase.datasource

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import cz.dcervenka.choretracker.core.model.error.AppError
import java.io.IOException

private const val AUTH_ERROR_INVALID_EMAIL = "ERROR_INVALID_EMAIL"

// FirebaseAuthWeakPasswordException extends FirebaseAuthInvalidCredentialsException, so it must
// be matched first.
@Suppress("CyclomaticComplexMethod")
internal fun Throwable.toAppError(): AppError = when (this) {
    is FirebaseNetworkException, is IOException -> AppError.NETWORK
    is FirebaseTooManyRequestsException -> AppError.TOO_MANY_REQUESTS
    is FirebaseAuthWeakPasswordException -> AppError.WEAK_PASSWORD
    is FirebaseAuthInvalidCredentialsException ->
        if (errorCode == AUTH_ERROR_INVALID_EMAIL) AppError.INVALID_EMAIL else AppError.INVALID_CREDENTIALS
    is FirebaseAuthInvalidUserException -> AppError.INVALID_CREDENTIALS
    is FirebaseAuthUserCollisionException -> AppError.EMAIL_ALREADY_IN_USE
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PERMISSION_DENIED
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        -> AppError.NETWORK
        else -> AppError.UNKNOWN
    }
    is FirebaseFunctionsException -> when (code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> AppError.NOT_SIGNED_IN
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> AppError.PERMISSION_DENIED
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        -> AppError.NETWORK
        else -> AppError.UNKNOWN
    }
    else -> AppError.UNKNOWN
}
