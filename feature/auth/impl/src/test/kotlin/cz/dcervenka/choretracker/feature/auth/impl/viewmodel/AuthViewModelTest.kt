package cz.dcervenka.choretracker.feature.auth.impl.viewmodel

import android.app.Application
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.domain.usecase.ContinueInPreviewModeUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SignInUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SignUpUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.test.mock.sampleAuthenticatedState
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import cz.dcervenka.choretracker.feature.auth.impl.contract.AuthUiIntent
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AuthViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK
    lateinit var observeAuthStateUseCase: ObserveAuthStateUseCase

    @MockK
    lateinit var application: Application

    @MockK
    lateinit var signInUseCase: SignInUseCase

    @MockK
    lateinit var signUpUseCase: SignUpUseCase

    @MockK
    lateinit var continueInPreviewModeUseCase: ContinueInPreviewModeUseCase

    private val authStateFlow = MutableStateFlow<AuthState>(sampleAuthenticatedState())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        authStateFlow.value = sampleAuthenticatedState()
        every { observeAuthStateUseCase() } returns authStateFlow
        every { application.getString(R.string.auth_signing_in) } returns "Signing in"
        every { application.getString(R.string.auth_creating_account) } returns "Creating account"
        every { application.getString(R.string.auth_opening_preview) } returns "Opening preview"
        coEvery { signInUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { signUpUseCase(any(), any(), any()) } returns AppResult.Success(Unit)
        coEvery { continueInPreviewModeUseCase(any()) } returns AppResult.Success(Unit)
    }

    @Test
    fun `sign in error updates ui state with message`() = runTest(coroutineRule.dispatcher) {
        coEvery { signInUseCase("dana@example.com", "wrong-password") } returns AppResult.Error("Invalid credentials")
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertThat(viewModel.uiState.value.errorMessage).isNull()

        viewModel.dispatch(AuthUiIntent.EmailChanged("dana@example.com"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.email).isEqualTo("dana@example.com")

        viewModel.dispatch(AuthUiIntent.PasswordChanged("wrong-password"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.password).isEqualTo("wrong-password")

        viewModel.dispatch(AuthUiIntent.SignInClicked)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isWorking).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo("Invalid credentials")
        coVerify { signInUseCase("dana@example.com", "wrong-password") }
    }

    @Test
    fun `continue preview uses fallback display name when blank`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.dispatch(AuthUiIntent.ContinuePreviewClicked)
        advanceUntilIdle()

        coVerify { continueInPreviewModeUseCase("Preview User") }
    }

    @Test
    fun `sign up with missing email and password surfaces validation instead of calling use case`() =
        runTest(coroutineRule.dispatcher) {
            val viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.dispatch(AuthUiIntent.DisplayNameChanged("David"))
            advanceUntilIdle()

            viewModel.dispatch(AuthUiIntent.SignUpClicked)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.errorMessage).isEqualTo("Email is required.")
            coVerify(exactly = 0) {
                signUpUseCase(any(), any(), any())
            }
        }

    @Test
    fun `sign up with missing display name is rejected before submission`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.dispatch(AuthUiIntent.EmailChanged("dana@example.com"))
        advanceUntilIdle()
        viewModel.dispatch(AuthUiIntent.PasswordChanged("password123"))
        advanceUntilIdle()

        viewModel.dispatch(AuthUiIntent.SignUpClicked)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage).isEqualTo("Display name is required.")
        coVerify(exactly = 0) { signUpUseCase(any(), any(), any()) }
    }

    @Test
    fun `requires configuration mirrors auth state`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertThat(viewModel.uiState.value.requiresConfiguration).isFalse()

        authStateFlow.value = AuthState.RequiresConfiguration
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.requiresConfiguration).isTrue()
    }
}

private fun AuthViewModelTest.createViewModel() = AuthViewModel(
    application = application,
    observeAuthStateUseCase = observeAuthStateUseCase,
    signInUseCase = signInUseCase,
    signUpUseCase = signUpUseCase,
    continueInPreviewModeUseCase = continueInPreviewModeUseCase,
)
