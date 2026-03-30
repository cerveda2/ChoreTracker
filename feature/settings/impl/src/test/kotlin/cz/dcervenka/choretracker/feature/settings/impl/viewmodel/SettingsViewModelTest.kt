package cz.dcervenka.choretracker.feature.settings.impl.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SignOutUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import cz.dcervenka.choretracker.core.test.mock.sampleAuthenticatedState
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK
    lateinit var observeAuthStateUseCase: ObserveAuthStateUseCase

    @MockK
    lateinit var signOutUseCase: SignOutUseCase

    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.SignedOut)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        authStateFlow.value = AuthState.SignedOut
        every { observeAuthStateUseCase() } returns authStateFlow
        coEvery { signOutUseCase() } returns cz.dcervenka.choretracker.core.common.AppResult.Success(Unit)
    }

    @Test
    fun `maps authenticated user into ui state`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        authStateFlow.value = sampleAuthenticatedState()

        viewModel.uiState.test {
            assertThat(awaitItem().userLabel).isNull()

            val authenticated = awaitItem()
            assertThat(authenticated.userLabel).isEqualTo("Dana")
            assertThat(authenticated.isSignedOut).isFalse()
        }
    }

    @Test
    fun `sign out delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.signOut()
        advanceUntilIdle()

        coVerify { signOutUseCase() }
    }
}

private fun SettingsViewModelTest.createViewModel() = SettingsViewModel(
    observeAuthStateUseCase = observeAuthStateUseCase,
    signOutUseCase = signOutUseCase,
)
