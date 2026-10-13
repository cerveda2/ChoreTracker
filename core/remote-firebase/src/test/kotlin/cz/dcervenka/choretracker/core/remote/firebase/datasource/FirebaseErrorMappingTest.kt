package cz.dcervenka.choretracker.core.remote.firebase.datasource

import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import cz.dcervenka.choretracker.core.model.error.AppError
import org.junit.Test
import java.io.IOException

class FirebaseErrorMappingTest {

    @Test
    fun `connectivity failures map to NETWORK`() {
        assertThat(FirebaseNetworkException("offline").toAppError()).isEqualTo(AppError.NETWORK)
        assertThat(IOException("Sync timed out").toAppError()).isEqualTo(AppError.NETWORK)
        assertThat(
            FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE).toAppError(),
        ).isEqualTo(AppError.NETWORK)
    }

    @Test
    fun `firestore codes map to their AppError or UNKNOWN`() {
        assertThat(
            FirebaseFirestoreException("nope", FirebaseFirestoreException.Code.PERMISSION_DENIED).toAppError(),
        ).isEqualTo(AppError.PERMISSION_DENIED)
        assertThat(
            FirebaseFirestoreException("bad", FirebaseFirestoreException.Code.INVALID_ARGUMENT).toAppError(),
        ).isEqualTo(AppError.UNKNOWN)
    }

    @Test
    fun `auth exceptions map to their AppError`() {
        assertThat(
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "wrong").toAppError(),
        ).isEqualTo(AppError.INVALID_CREDENTIALS)
        assertThat(
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "malformed").toAppError(),
        ).isEqualTo(AppError.INVALID_EMAIL)
        assertThat(
            FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "missing").toAppError(),
        ).isEqualTo(AppError.INVALID_CREDENTIALS)
        assertThat(
            FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "taken").toAppError(),
        ).isEqualTo(AppError.EMAIL_ALREADY_IN_USE)
        assertThat(FirebaseTooManyRequestsException("slow down").toAppError()).isEqualTo(AppError.TOO_MANY_REQUESTS)
    }

    @Test
    fun `weak password is not swallowed by its invalid-credentials superclass`() {
        val weakPassword = FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "weak", "too short")

        assertThat(weakPassword.toAppError()).isEqualTo(AppError.WEAK_PASSWORD)
    }

    @Test
    fun `an unrelated throwable maps to UNKNOWN`() {
        assertThat(IllegalStateException("boom").toAppError()).isEqualTo(AppError.UNKNOWN)
    }
}
