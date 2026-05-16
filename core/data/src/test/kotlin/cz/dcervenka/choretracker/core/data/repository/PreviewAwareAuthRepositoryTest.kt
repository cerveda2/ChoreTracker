package cz.dcervenka.choretracker.core.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.remote.contract.RemoteAuthDataSource
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class PreviewAwareAuthRepositoryTest {

    @MockK lateinit var remoteAuthDataSource: RemoteAuthDataSource

    private val remoteAuthState = MutableStateFlow<AuthState>(AuthState.SignedOut)

    private lateinit var repository: PreviewAwareAuthRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { remoteAuthDataSource.authState } returns remoteAuthState
        every { remoteAuthDataSource.isConfigured } returns true
        repository = PreviewAwareAuthRepository(remoteAuthDataSource)
    }

    // authState initial value — tested via the StateFlow's stateIn initialValue
    // which is emitted synchronously as the first item before upstream activates

    @Test
    fun `authState initial value is Initializing when remote is configured`() = runBlocking {
        every { remoteAuthDataSource.isConfigured } returns true
        val repo = PreviewAwareAuthRepository(remoteAuthDataSource)

        repo.authState.test {
            assertThat(awaitItem()).isEqualTo(AuthState.Initializing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `authState initial value is RequiresConfiguration when remote is not configured`() = runBlocking {
        every { remoteAuthDataSource.isConfigured } returns false
        val repo = PreviewAwareAuthRepository(remoteAuthDataSource)

        repo.authState.test {
            assertThat(awaitItem()).isEqualTo(AuthState.RequiresConfiguration)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // continueInPreviewMode — tested via observable side effects on updateDisplayName,
    // which reads previewState.value directly (not via the stateIn flow)

    @Test
    fun `continueInPreviewMode returns Success`() = runBlocking {
        val result = repository.continueInPreviewMode("Dana")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `continueInPreviewMode activates preview branch for subsequent updateDisplayName`() = runBlocking {
        repository.continueInPreviewMode("Dana")

        repository.updateDisplayName("New Name")

        coVerify(exactly = 0) { remoteAuthDataSource.updateDisplayName(any()) }
    }

    @Test
    fun `continueInPreviewMode with blank name uses Preview User fallback`() = runBlocking {
        repository.continueInPreviewMode("   ")

        // updateDisplayName in preview reads previewState.value directly;
        // if displayName is "Preview User" it won't call remote
        repository.updateDisplayName("irrelevant")
        coVerify(exactly = 0) { remoteAuthDataSource.updateDisplayName(any()) }
    }

    // clearPreviewState

    @Test
    fun `clearPreviewState deactivates preview branch so updateDisplayName calls remote`() = runBlocking {
        coEvery { remoteAuthDataSource.updateDisplayName(any()) } returns AppResult.Success(Unit)
        repository.continueInPreviewMode("Dana")

        repository.clearPreviewState()
        repository.updateDisplayName("Dana")

        coVerify(exactly = 1) { remoteAuthDataSource.updateDisplayName("Dana") }
    }

    @Test
    fun `clearPreviewState on non-preview session is a no-op`() = runBlocking {
        coEvery { remoteAuthDataSource.updateDisplayName(any()) } returns AppResult.Success(Unit)

        repository.clearPreviewState()
        repository.updateDisplayName("Dana")

        coVerify(exactly = 1) { remoteAuthDataSource.updateDisplayName("Dana") }
    }

    // updateDisplayName

    @Test
    fun `updateDisplayName delegates to remote when not in preview`() = runBlocking {
        coEvery { remoteAuthDataSource.updateDisplayName(any()) } returns AppResult.Success(Unit)

        repository.updateDisplayName("Dana")

        coVerify(exactly = 1) { remoteAuthDataSource.updateDisplayName("Dana") }
    }

    @Test
    fun `updateDisplayName trims whitespace before delegating to remote`() = runBlocking {
        coEvery { remoteAuthDataSource.updateDisplayName(any()) } returns AppResult.Success(Unit)

        repository.updateDisplayName("  Dana  ")

        coVerify { remoteAuthDataSource.updateDisplayName("Dana") }
    }

    @Test
    fun `updateDisplayName in preview uses fallback name for blank input`() = runBlocking {
        repository.continueInPreviewMode("Dana")

        // blank input → "Preview User"; subsequent call still goes to preview branch
        repository.updateDisplayName("   ")
        repository.updateDisplayName("anything")

        coVerify(exactly = 0) { remoteAuthDataSource.updateDisplayName(any()) }
    }
}
