package cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.LogCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentDashboardUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveRecentCompletionsUseCase
import cz.dcervenka.choretracker.core.test.mock.sampleDashboardSnapshot
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import cz.dcervenka.choretracker.core.test.mock.sampleMembers
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
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

class DashboardViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK
    lateinit var observeCurrentDashboardUseCase: ObserveCurrentDashboardUseCase

    @MockK
    lateinit var observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase

    @MockK
    lateinit var observeMembersUseCase: ObserveMembersUseCase

    @MockK
    lateinit var observeRecentCompletionsUseCase: ObserveRecentCompletionsUseCase

    @MockK
    lateinit var logCompletionUseCase: LogCompletionUseCase

    private val dashboardFlow = MutableStateFlow(sampleDashboardSnapshot())
    private val householdFlow = MutableStateFlow<cz.dcervenka.choretracker.core.model.household.Household?>(null)
    private val membersFlow =
        MutableStateFlow(emptyList<cz.dcervenka.choretracker.core.model.household.HouseholdMember>())
    private val completionsFlow = MutableStateFlow(sampleDashboardSnapshot().recentCompletions)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        dashboardFlow.value = sampleDashboardSnapshot()
        householdFlow.value = null
        membersFlow.value = emptyList()
        every { observeCurrentDashboardUseCase() } returns dashboardFlow
        every { observeCurrentHouseholdUseCase() } returns householdFlow
        every { observeMembersUseCase(any()) } answers { membersFlow }
        every { observeRecentCompletionsUseCase(any(), any()) } answers { completionsFlow }
        coEvery { logCompletionUseCase(any(), any(), any(), any()) } returns AppResult.Success(Unit)
    }

    @Test
    fun `ui state combines snapshot and members from current household`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().snapshot).isNull()

            membersFlow.value = sampleMembers()
            householdFlow.value = sampleHousehold()
            dashboardFlow.value = sampleDashboardSnapshot()

            val state = awaitItem()
            assertThat(state.snapshot?.household?.id).isEqualTo("household-1")
            assertThat(state.members).hasSize(2)
            assertThat(state.allCompletions).hasSize(1)
        }
    }

    @Test
    fun `log completion delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.logCompletion(
            householdId = "household-1",
            choreId = "chore-1",
            participantIds = listOf("member-1", "member-2"),
            note = "Done together",
        )
        advanceUntilIdle()

        coVerify {
            logCompletionUseCase("household-1", "chore-1", listOf("member-1", "member-2"), "Done together")
        }
    }
}

private fun DashboardViewModelTest.createViewModel() = DashboardViewModel(
    observeCurrentDashboardUseCase = observeCurrentDashboardUseCase,
    observeCurrentHouseholdUseCase = observeCurrentHouseholdUseCase,
    observeMembersUseCase = observeMembersUseCase,
    observeRecentCompletionsUseCase = observeRecentCompletionsUseCase,
    logCompletionUseCase = logCompletionUseCase,
)
