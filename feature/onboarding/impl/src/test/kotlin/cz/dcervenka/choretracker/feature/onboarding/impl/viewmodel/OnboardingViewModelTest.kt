package cz.dcervenka.choretracker.feature.onboarding.impl.viewmodel

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.CreateHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.JoinHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveHouseholdRestoreStatusUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.error.AppError
import cz.dcervenka.choretracker.core.model.household.HouseholdRestoreStatus
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiIntent
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

class OnboardingViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK
    lateinit var createHouseholdUseCase: CreateHouseholdUseCase

    @MockK
    lateinit var joinHouseholdUseCase: JoinHouseholdUseCase

    @MockK
    lateinit var observeAuthStateUseCase: ObserveAuthStateUseCase

    @MockK
    lateinit var observeHouseholdRestoreStatusUseCase: ObserveHouseholdRestoreStatusUseCase

    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private val restoreStatusFlow = MutableStateFlow(HouseholdRestoreStatus())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        authStateFlow.value = AuthState.SignedOut
        every { observeAuthStateUseCase() } returns authStateFlow
        every { observeHouseholdRestoreStatusUseCase() } returns restoreStatusFlow
        coEvery { createHouseholdUseCase(any(), any()) } returns AppResult.Success(
            cz.dcervenka.choretracker.core.test.mock.sampleHousehold(),
        )
        coEvery { joinHouseholdUseCase(any(), any()) } returns AppResult.Success(
            cz.dcervenka.choretracker.core.test.mock.sampleHousehold(),
        )
    }

    @Test
    fun `create household delegates to use case and clears working state`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertThat(viewModel.uiState.value.isWorking).isFalse()

        viewModel.dispatch(OnboardingUiIntent.HouseholdNameChanged("Family Home"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.householdName).isEqualTo("Family Home")

        viewModel.dispatch(OnboardingUiIntent.DisplayNameChanged("Dana"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.displayName).isEqualTo("Dana")

        viewModel.dispatch(OnboardingUiIntent.CreateHousehold)
        advanceUntilIdle()

        coVerify { createHouseholdUseCase("Family Home", "Dana") }
        assertThat(viewModel.uiState.value.isWorking).isFalse()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `join household error updates message`() = runTest(coroutineRule.dispatcher) {
        coEvery { joinHouseholdUseCase("BADCODE", "Dana") } returns AppResult.Error(AppError.INVITE_NOT_FOUND)
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertThat(viewModel.uiState.value.error).isNull()

        viewModel.dispatch(OnboardingUiIntent.InviteCodeChanged("BADCODE"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.inviteCode).isEqualTo("BADCODE")

        viewModel.dispatch(OnboardingUiIntent.DisplayNameChanged("Dana"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.displayName).isEqualTo("Dana")

        viewModel.dispatch(OnboardingUiIntent.JoinHousehold)
        advanceUntilIdle()

        coVerify { joinHouseholdUseCase("BADCODE", "Dana") }
        assertThat(viewModel.uiState.value.isWorking).isFalse()
        assertThat(viewModel.uiState.value.error).isEqualTo(AppError.INVITE_NOT_FOUND)

        viewModel.dispatch(OnboardingUiIntent.ClearError)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `restore error is exposed in onboarding state`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        restoreStatusFlow.value = HouseholdRestoreStatus(
            error = AppError.PERMISSION_DENIED,
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.restoreError).isEqualTo(AppError.PERMISSION_DENIED)
    }
}

private fun OnboardingViewModelTest.createViewModel() = OnboardingViewModel(
    observeAuthStateUseCase = observeAuthStateUseCase,
    observeHouseholdRestoreStatusUseCase = observeHouseholdRestoreStatusUseCase,
    createHouseholdUseCase = createHouseholdUseCase,
    joinHouseholdUseCase = joinHouseholdUseCase,
)
