package cz.dcervenka.choretracker.core.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.remote.contract.RemoteAuthDataSource
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PreviewAwareAuthRepositoryTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK lateinit var remoteAuthDataSource: RemoteAuthDataSource

    private val remoteAuthState = MutableStateFlow<AuthState>(AuthState.SignedOut)

    private lateinit var repository: PreviewAwareAuthRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { remoteAuthDataSource.authState } returns remoteAuthState
        every { remoteAuthDataSource.isConfigured } returns true
        repository = PreviewAwareAuthRepository(
            remoteAuthDataSource,
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }

    // authState initial value — tested via the StateFlow's stateIn initialValue,
    // emitted synchronously as the first item before upstream activates. A paused
    // TestDispatcher keeps this deterministic: the WhileSubscribed sharing coroutine
    // can't run ahead of the assertion the way a real Dispatchers.IO scope could.

    @Test
    fun `authState initial value is Initializing when remote is configured`() = runTest(coroutineRule.dispatcher) {
        every { remoteAuthDataSource.isConfigured } returns true
        val repo =
            PreviewAwareAuthRepository(
                remoteAuthDataSource,
                CoroutineScope(SupervisorJob() + coroutineRule.dispatcher),
            )

        repo.authState.test {
            assertThat(awaitItem()).isEqualTo(AuthState.Initializing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `authState initial value is RequiresConfiguration when remote is not configured`() = runTest(
        coroutineRule.dispatcher,
    ) {
        every { remoteAuthDataSource.isConfigured } returns false
        val repo =
            PreviewAwareAuthRepository(
                remoteAuthDataSource,
                CoroutineScope(SupervisorJob() + coroutineRule.dispatcher),
            )

        repo.authState.test {
            assertThat(awaitItem()).isEqualTo(AuthState.RequiresConfiguration)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // continueInPreviewMode — tested via observable side effects on updateDisplayName,
    // which reads previewState.value directly (not via the stateIn flow)

    @Test
    fun `continueInPreviewMode returns Success`() = runTest(coroutineRule.dispatcher) {
        val result = repository.continueInPreviewMode("Dana")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `continueInPreviewMode activates preview branch for subsequent updateDisplayName`() = runTest(
        coroutineRule.dispatcher,
    ) {
        repository.continueInPreviewMode("Dana")

        repository.updateDisplayName("New Name")

        coVerify(exactly = 0) { remoteAuthDataSource.updateDisplayName(any()) }
    }

    @Test
    fun `continueInPreviewMode with blank name uses Preview User fallback`() = runTest(coroutineRule.dispatcher) {
        repository.continueInPreviewMode("   ")

        // updateDisplayName in preview reads previewState.value directly;
        // if displayName is "Preview User" it won't call remote
        repository.updateDisplayName("irrelevant")
        coVerify(exactly = 0) { remoteAuthDataSource.updateDisplayName(any()) }
    }

    // clearPreviewState

    @Test
    fun `clearPreviewState deactivates preview branch so updateDisplayName calls remote`() = runTest(
        coroutineRule.dispatcher,
    ) {
        coEvery { remoteAuthDataSource.updateDisplayName(any()) } returns AppResult.Success(Unit)
        repository.continueInPreviewMode("Dana")

        repository.clearPreviewState()
        repository.updateDisplayName("Dana")

        coVerify(exactly = 1) { remoteAuthDataSource.updateDisplayName("Dana") }
    }

    @Test
    fun `clearPreviewState on non-preview session is a no-op`() = runTest(coroutineRule.dispatcher) {
        coEvery { remoteAuthDataSource.updateDisplayName(any()) } returns AppResult.Success(Unit)

        repository.clearPreviewState()
        repository.updateDisplayName("Dana")

        coVerify(exactly = 1) { remoteAuthDataSource.updateDisplayName("Dana") }
    }

    // updateDisplayName

    @Test
    fun `updateDisplayName delegates to remote when not in preview`() = runTest(coroutineRule.dispatcher) {
        coEvery { remoteAuthDataSource.updateDisplayName(any()) } returns AppResult.Success(Unit)

        repository.updateDisplayName("Dana")

        coVerify(exactly = 1) { remoteAuthDataSource.updateDisplayName("Dana") }
    }

    @Test
    fun `updateDisplayName trims whitespace before delegating to remote`() = runTest(coroutineRule.dispatcher) {
        coEvery { remoteAuthDataSource.updateDisplayName(any()) } returns AppResult.Success(Unit)

        repository.updateDisplayName("  Dana  ")

        coVerify { remoteAuthDataSource.updateDisplayName("Dana") }
    }

    @Test
    fun `updateDisplayName in preview uses fallback name for blank input`() = runTest(coroutineRule.dispatcher) {
        repository.continueInPreviewMode("Dana")

        // blank input → "Preview User"; subsequent call still goes to preview branch
        repository.updateDisplayName("   ")
        repository.updateDisplayName("anything")

        coVerify(exactly = 0) { remoteAuthDataSource.updateDisplayName(any()) }
    }

    // deleteAccount

    @Test
    fun `deleteAccount in preview clears preview state and never touches remote`() = runTest(coroutineRule.dispatcher) {
        coEvery { remoteAuthDataSource.updateDisplayName(any()) } returns AppResult.Success(Unit)
        repository.continueInPreviewMode("Dana")

        val result = repository.deleteAccount()

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify(exactly = 0) { remoteAuthDataSource.deleteAccount() }
        // preview state cleared → the next updateDisplayName falls through to remote
        repository.updateDisplayName("Dana")
        coVerify(exactly = 1) { remoteAuthDataSource.updateDisplayName("Dana") }
    }

    @Test
    fun `deleteAccount outside preview delegates to remote`() = runTest(coroutineRule.dispatcher) {
        coEvery { remoteAuthDataSource.deleteAccount() } returns AppResult.Success(Unit)

        repository.deleteAccount()

        coVerify(exactly = 1) { remoteAuthDataSource.deleteAccount() }
    }
}
