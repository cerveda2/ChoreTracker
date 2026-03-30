package cz.dcervenka.choretracker.feature.onboarding.impl.viewmodel

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.CreateHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.JoinHouseholdUseCase
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
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

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
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

        viewModel.onHouseholdNameChange("Family Home")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.householdName).isEqualTo("Family Home")

        viewModel.onDisplayNameChange("Dana")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.displayName).isEqualTo("Dana")

        viewModel.createHousehold()
        advanceUntilIdle()

        coVerify { createHouseholdUseCase("Family Home", "Dana") }
        assertThat(viewModel.uiState.value.isWorking).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `join household error updates message`() = runTest(coroutineRule.dispatcher) {
        coEvery { joinHouseholdUseCase("BADCODE", "Dana") } returns AppResult.Error("Invite code is invalid")
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertThat(viewModel.uiState.value.errorMessage).isNull()

        viewModel.onInviteCodeChange("BADCODE")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.inviteCode).isEqualTo("BADCODE")

        viewModel.onDisplayNameChange("Dana")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.displayName).isEqualTo("Dana")

        viewModel.joinHousehold()
        advanceUntilIdle()

        coVerify { joinHouseholdUseCase("BADCODE", "Dana") }
        assertThat(viewModel.uiState.value.isWorking).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo("Invite code is invalid")
    }
}

private fun OnboardingViewModelTest.createViewModel() = OnboardingViewModel(
    createHouseholdUseCase = createHouseholdUseCase,
    joinHouseholdUseCase = joinHouseholdUseCase,
)
