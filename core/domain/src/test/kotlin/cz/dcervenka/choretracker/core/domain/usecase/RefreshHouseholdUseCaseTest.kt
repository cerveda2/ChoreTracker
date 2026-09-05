package cz.dcervenka.choretracker.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.test.mock.sampleAuthenticatedState
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RefreshHouseholdUseCaseTest {

    @MockK
    lateinit var authRepository: AuthRepository

    @MockK
    lateinit var syncRepository: SyncRepository

    private val authStateFlow = MutableStateFlow<AuthState>(sampleAuthenticatedState())

    private lateinit var useCase: RefreshHouseholdUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { authRepository.authState } returns authStateFlow
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Success(Unit)
        coEvery { syncRepository.restoreHouseholdForUser(any()) } returns AppResult.Success(true)
        useCase = RefreshHouseholdUseCase(authRepository, syncRepository)
    }

    @Test
    fun `pushes pending operations then pulls the latest snapshot for the authenticated user`() = runTest {
        val result = useCase()

        coVerify(exactly = 1) { syncRepository.syncPendingOperations() }
        coVerify(exactly = 1) { syncRepository.restoreHouseholdForUser("user-1") }
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `skips the restore for a preview user`() = runTest {
        authStateFlow.value = AuthState.Authenticated(
            AppUser(id = "preview-user", email = null, displayName = "Preview User", isPreview = true),
        )

        val result = useCase()

        coVerify(exactly = 0) { syncRepository.restoreHouseholdForUser(any()) }
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `skips the restore when signed out`() = runTest {
        authStateFlow.value = AuthState.SignedOut

        val result = useCase()

        coVerify(exactly = 0) { syncRepository.restoreHouseholdForUser(any()) }
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `surfaces an error from the restore`() = runTest {
        coEvery { syncRepository.restoreHouseholdForUser(any()) } returns AppResult.Error("Network error")

        val result = useCase()

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        assertThat((result as AppResult.Error).message).isEqualTo("Network error")
    }

    @Test
    fun `surfaces an error from the push even when the pull succeeds`() = runTest {
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Error("Push failed")

        val result = useCase()

        coVerify(exactly = 1) { syncRepository.restoreHouseholdForUser("user-1") }
        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        assertThat((result as AppResult.Error).message).isEqualTo("Push failed")
    }

    @Test
    fun `a pull failure takes precedence over a push failure`() = runTest {
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Error("Push failed")
        coEvery { syncRepository.restoreHouseholdForUser(any()) } returns AppResult.Error("Pull failed")

        val result = useCase()

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        assertThat((result as AppResult.Error).message).isEqualTo("Pull failed")
    }
}
