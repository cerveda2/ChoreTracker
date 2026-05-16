package cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.DeleteCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.LogCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentDashboardUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveRecentCompletionsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveSyncStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.RetryPendingSyncUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateCompletionUseCase
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.sync.SyncState
import cz.dcervenka.choretracker.core.test.mock.sampleChore
import cz.dcervenka.choretracker.core.test.mock.sampleDashboardSnapshot
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import cz.dcervenka.choretracker.core.test.mock.sampleMembers
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiIntent
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

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
    lateinit var observeSyncStateUseCase: ObserveSyncStateUseCase

    @MockK
    lateinit var logCompletionUseCase: LogCompletionUseCase

    @MockK
    lateinit var updateCompletionUseCase: UpdateCompletionUseCase

    @MockK
    lateinit var deleteCompletionUseCase: DeleteCompletionUseCase

    @MockK
    lateinit var retryPendingSyncUseCase: RetryPendingSyncUseCase

    private val dashboardFlow = MutableStateFlow(sampleDashboardSnapshot())
    private val householdFlow = MutableStateFlow<Household?>(null)
    private val membersFlow = MutableStateFlow(emptyList<HouseholdMember>())
    private val completionsFlow = MutableStateFlow(sampleDashboardSnapshot().recentCompletions)
    private val syncStateFlow = MutableStateFlow<SyncState?>(null)

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
        every { observeSyncStateUseCase(any()) } answers { syncStateFlow }
        coEvery { logCompletionUseCase(any(), any(), any(), any()) } returns AppResult.Success("completion-id")
        coEvery { deleteCompletionUseCase(any()) } returns AppResult.Success(Unit)
        coEvery { retryPendingSyncUseCase() } returns AppResult.Success(Unit)
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
    fun `ui state exposes sync status for dashboard banner`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().syncState).isNull()

            householdFlow.value = sampleHousehold()
            syncStateFlow.value = SyncState(
                householdId = "household-1",
                lastSyncedAt = null,
                lastSyncAttemptAt = Instant.parse("2026-04-10T05:24:20Z"),
                pendingOperations = 2,
                lastErrorMessage = "Missing or insufficient permissions.",
            )

            val state = awaitItem()
            assertThat(state.syncState?.pendingOperations).isEqualTo(2)
            assertThat(state.syncState?.lastErrorMessage).contains("permissions")
        }
    }

    @Test
    fun `log completion delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.dispatch(
            DashboardUiIntent.LogCompletion(
                householdId = "household-1",
                choreId = "chore-1",
                participantIds = listOf("member-1", "member-2"),
                note = "Done together",
                completedAt = null,
            )
        )
        advanceUntilIdle()

        coVerify {
            logCompletionUseCase("household-1", "chore-1", listOf("member-1", "member-2"), "Done together")
        }
    }

    @Test
    fun `retry sync delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.dispatch(DashboardUiIntent.RetrySync)
        advanceUntilIdle()

        coVerify { retryPendingSyncUseCase() }
    }

    @Test
    fun `log completion emits undo event with completion id and chore name`() = runTest(coroutineRule.dispatcher) {
        dashboardFlow.value = sampleDashboardSnapshot().copy(activeChores = listOf(sampleChore()))
        householdFlow.value = sampleHousehold()
        val viewModel = createViewModel()

        viewModel.undoEvents.test {
            val stateJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.dispatch(
                DashboardUiIntent.LogCompletion(
                    householdId = "household-1",
                    choreId = "chore-1",
                    participantIds = listOf("member-1"),
                    note = null,
                    completedAt = null,
                )
            )
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(UndoEvent("completion-id", "Kitchen"))
            stateJob.cancel()
        }
    }
}

private fun DashboardViewModelTest.createViewModel() = DashboardViewModel(
    observeCurrentDashboardUseCase = observeCurrentDashboardUseCase,
    observeCurrentHouseholdUseCase = observeCurrentHouseholdUseCase,
    observeMembersUseCase = observeMembersUseCase,
    observeRecentCompletionsUseCase = observeRecentCompletionsUseCase,
    observeSyncStateUseCase = observeSyncStateUseCase,
    logCompletionUseCase = logCompletionUseCase,
    updateCompletionUseCase = updateCompletionUseCase,
    deleteCompletionUseCase = deleteCompletionUseCase,
    retryPendingSyncUseCase = retryPendingSyncUseCase,
)
