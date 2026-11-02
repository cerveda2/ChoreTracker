package cz.dcervenka.choretracker.core.remote.firebase.datasource

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import kotlinx.coroutines.CancellationException
import org.junit.Test
import kotlin.time.Instant

class FirebaseHouseholdDataSourceTest {

    // rethrowCancellation

    @Test
    fun `rethrowCancellation rethrows CancellationException instead of wrapping it`() {
        val cancellation = CancellationException("scope torn down")
        val failureResult = Result.failure<Unit>(cancellation)

        var thrown: Throwable? = null
        try {
            failureResult.rethrowCancellation()
        } catch (e: CancellationException) {
            thrown = e
        }

        assertThat(thrown).isSameInstanceAs(cancellation)
    }

    @Test
    fun `rethrowCancellation leaves a regular failure wrapped as a Result failure`() {
        val error = IllegalStateException("network error")
        val failureResult = Result.failure<Unit>(error)

        val result = failureResult.rethrowCancellation()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isSameInstanceAs(error)
    }

    @Test
    fun `rethrowCancellation leaves a success untouched`() {
        val successResult = Result.success("value")

        val result = successResult.rethrowCancellation()

        assertThat(result.getOrNull()).isEqualTo("value")
    }

    // isPermissionDenied

    @Test
    fun `isPermissionDenied is true only for a PERMISSION_DENIED FirebaseFirestoreException`() {
        val permissionDenied = FirebaseFirestoreException("nope", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        val unavailable = FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
        val unrelated = IllegalStateException("boom")

        assertThat(permissionDenied.isPermissionDenied()).isTrue()
        assertThat(unavailable.isPermissionDenied()).isFalse()
        assertThat(unrelated.isPermissionDenied()).isFalse()
    }

    // toFirestoreFields

    private val activeMember = HouseholdMember(
        id = "member-1",
        householdId = "household-1",
        userId = "user-1",
        displayName = "Dana",
        role = HouseholdRole.MEMBER,
        email = "dana@example.com",
    )

    @Test
    fun `toFirestoreFields keeps an active member's email`() {
        val fields = activeMember.toFirestoreFields()

        assertThat(fields["email"]).isEqualTo("dana@example.com")
        assertThat(fields["active"]).isEqualTo(true)
    }

    @Test
    fun `toFirestoreFields leaves email untouched when an active member has none`() {
        val fields = activeMember.copy(email = null).toFirestoreFields()

        assertThat(fields).doesNotContainKey("email")
    }

    @Test
    fun `toFirestoreFields deletes a departed member's email but keeps their name`() {
        val removedAt = Instant.parse("2026-11-02T10:00:00Z")

        listOf(activeMember, activeMember.copy(email = null)).forEach { member ->
            val fields = member.copy(removedAt = removedAt).toFirestoreFields()

            assertThat(fields["email"]).isEqualTo(FieldValue.delete())
            assertThat(fields["displayName"]).isEqualTo("Dana")
            assertThat(fields["active"]).isEqualTo(false)
        }
    }

    // canHaveDepartedRemotely

    @Test
    fun `canHaveDepartedRemotely is true for another account still active locally`() {
        assertThat(activeMember.canHaveDepartedRemotely(currentUserId = "owner-1")).isTrue()
    }

    @Test
    fun `canHaveDepartedRemotely is false for the current user, whose doc a first push creates`() {
        assertThat(activeMember.canHaveDepartedRemotely(currentUserId = "user-1")).isFalse()
    }

    @Test
    fun `canHaveDepartedRemotely is false for a placeholder without an account`() {
        assertThat(activeMember.copy(userId = null).canHaveDepartedRemotely(currentUserId = "owner-1")).isFalse()
    }

    @Test
    fun `canHaveDepartedRemotely is false for a member already departed locally`() {
        val departed = activeMember.copy(removedAt = Instant.parse("2026-11-02T10:00:00Z"))

        assertThat(departed.canHaveDepartedRemotely(currentUserId = "owner-1")).isFalse()
    }
}
