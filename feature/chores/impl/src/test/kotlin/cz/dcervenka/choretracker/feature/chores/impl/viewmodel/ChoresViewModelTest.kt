package cz.dcervenka.choretracker.feature.chores.impl.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.AddChoreUseCase
import cz.dcervenka.choretracker.core.domain.usecase.DeleteChoreUseCase
import cz.dcervenka.choretracker.core.domain.usecase.DeleteCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.LogCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveChoresUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentDashboardUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreActiveUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreCategoryUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreFrequencyUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreNameUseCase
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.test.mock.sampleChore
import cz.dcervenka.choretracker.core.test.mock.sampleDashboardSnapshot
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import cz.dcervenka.choretracker.core.test.mock.sampleMembers
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import cz.dcervenka.choretracker.feature.chores.impl.contract.ChoreFilter
import cz.dcervenka.choretracker.feature.chores.impl.contract.ChoresUiEvent
import cz.dcervenka.choretracker.feature.chores.impl.contract.ChoresUiIntent
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

class ChoresViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK
    lateinit var observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase

    @MockK
    lateinit var observeChoresUseCase: ObserveChoresUseCase

    @MockK
    lateinit var observeMembersUseCase: ObserveMembersUseCase

    @MockK
    lateinit var observeCurrentDashboardUseCase: ObserveCurrentDashboardUseCase

    @MockK
    lateinit var addChoreUseCase: AddChoreUseCase

    @MockK
    lateinit var updateChoreNameUseCase: UpdateChoreNameUseCase

    @MockK
    lateinit var updateChoreCategoryUseCase: UpdateChoreCategoryUseCase

    @MockK
    lateinit var updateChoreFrequencyUseCase: UpdateChoreFrequencyUseCase

    @MockK
    lateinit var updateChoreActiveUseCase: UpdateChoreActiveUseCase

    @MockK
    lateinit var deleteChoreUseCase: DeleteChoreUseCase

    @MockK
    lateinit var logCompletionUseCase: LogCompletionUseCase

    @MockK
    lateinit var deleteCompletionUseCase: DeleteCompletionUseCase

    private val householdFlow = MutableStateFlow<Household?>(null)
    private val choresFlow = MutableStateFlow(emptyList<Chore>())
    private val membersFlow = MutableStateFlow(emptyList<HouseholdMember>())
    private val dashboardFlow = MutableStateFlow(sampleDashboardSnapshot())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        householdFlow.value = null
        choresFlow.value = emptyList()
        membersFlow.value = emptyList()
        dashboardFlow.value = sampleDashboardSnapshot()
        every { observeCurrentHouseholdUseCase() } returns householdFlow
        every { observeChoresUseCase(any()) } answers { choresFlow }
        every { observeMembersUseCase(any()) } answers { membersFlow }
        every { observeCurrentDashboardUseCase() } returns dashboardFlow
        coEvery { addChoreUseCase(any(), any(), any(), any()) } returns AppResult.Success(Unit)
        coEvery { updateChoreNameUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { updateChoreCategoryUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { updateChoreFrequencyUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { updateChoreActiveUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { deleteChoreUseCase(any()) } returns AppResult.Success(Unit)
        coEvery { logCompletionUseCase(any(), any(), any(), any(), any()) } returns AppResult.Success("completion-1")
        coEvery { deleteCompletionUseCase(any()) } returns AppResult.Success(Unit)
    }

    @Test
    fun `ui state combines household, chores and members, dropping soft-deleted chores`() =
        runTest(coroutineRule.dispatcher) {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                assertThat(awaitItem().householdId).isNull()

                householdFlow.value = sampleHousehold()
                membersFlow.value = sampleMembers()
                choresFlow.value = listOf(
                    sampleChore(),
                    sampleChore().copy(id = "chore-2", deletedAt = sampleChore().createdAt),
                )

                val state = awaitItem()
                assertThat(state.householdId).isEqualTo("household-1")
                assertThat(state.chores).hasSize(1)
                assertThat(state.chores.first().id).isEqualTo("chore-1")
                assertThat(state.members).hasSize(2)
                assertThat(state.isOwner).isTrue()
            }
        }

    @Test
    fun `filter and query intents update ui state`() = runTest(coroutineRule.dispatcher) {
        householdFlow.value = sampleHousehold()
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.dispatch(ChoresUiIntent.FilterChanged(ChoreFilter.OVERDUE))
            assertThat(awaitItem().filter).isEqualTo(ChoreFilter.OVERDUE)

            viewModel.dispatch(ChoresUiIntent.QueryChanged("dish"))
            assertThat(awaitItem().query).isEqualTo("dish")
        }
    }

    @Test
    fun `addChore delegates to use case and emits ChoreAdded`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.dispatch(
                ChoresUiIntent.AddChore(
                    householdId = "household-1",
                    name = "Vacuum",
                    category = ChoreCategory.CLEANING,
                    frequencyDays = 7,
                ),
            )
            advanceUntilIdle()

            coVerify { addChoreUseCase("household-1", "Vacuum", ChoreCategory.CLEANING, 7) }
            assertThat(awaitItem()).isEqualTo(ChoresUiEvent.ChoreAdded)
        }
    }

    @Test
    fun `addChore emits an error event when the use case fails`() = runTest(coroutineRule.dispatcher) {
        coEvery { addChoreUseCase(any(), any(), any(), any()) } returns AppResult.Error("Frequency must be positive.")
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.dispatch(
                ChoresUiIntent.AddChore(
                    householdId = "household-1",
                    name = "Vacuum",
                    category = ChoreCategory.CLEANING,
                    frequencyDays = -1,
                ),
            )
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(ChoresUiEvent.Error("Frequency must be positive."))
        }
    }

    @Test
    fun `updateChore only calls the use case for fields that actually changed`() = runTest(coroutineRule.dispatcher) {
        householdFlow.value = sampleHousehold()
        choresFlow.value = listOf(sampleChore())
        val viewModel = createViewModel()
        val stateJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.dispatch(
            ChoresUiIntent.UpdateChore(
                choreId = "chore-1",
                name = "Kitchen deep clean",
                category = sampleChore().category,
                frequencyDays = sampleChore().frequencyDays,
                isActive = sampleChore().isActive,
            ),
        )
        advanceUntilIdle()

        coVerify { updateChoreNameUseCase("chore-1", "Kitchen deep clean") }
        coVerify(exactly = 0) { updateChoreCategoryUseCase(any(), any()) }
        coVerify(exactly = 0) { updateChoreFrequencyUseCase(any(), any()) }
        coVerify(exactly = 0) { updateChoreActiveUseCase(any(), any()) }
        stateJob.cancel()
    }

    @Test
    fun `deleteChore delegates to use case and emits ChoreDeleted`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.dispatch(ChoresUiIntent.DeleteChore("chore-1"))
            advanceUntilIdle()

            coVerify { deleteChoreUseCase("chore-1") }
            assertThat(awaitItem()).isEqualTo(ChoresUiEvent.ChoreDeleted)
        }
    }

    @Test
    fun `logCompletion success emits an undo event with the chore name`() = runTest(coroutineRule.dispatcher) {
        householdFlow.value = sampleHousehold()
        choresFlow.value = listOf(sampleChore())
        val viewModel = createViewModel()
        val stateJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.undoEvents.test {
            viewModel.dispatch(
                ChoresUiIntent.LogCompletion(
                    householdId = "household-1",
                    choreId = "chore-1",
                    participantIds = listOf("member-1"),
                    note = null,
                    completedAt = null,
                ),
            )
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(UndoEvent("completion-1", "Kitchen"))
        }
        stateJob.cancel()
    }

    @Test
    fun `deleteCompletion emits an error event when the use case fails`() = runTest(coroutineRule.dispatcher) {
        coEvery { deleteCompletionUseCase(any()) } returns AppResult.Error("Not allowed.")
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.dispatch(ChoresUiIntent.DeleteCompletion("completion-1"))
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(ChoresUiEvent.Error("Not allowed."))
        }
    }
}

private fun ChoresViewModelTest.createViewModel() = ChoresViewModel(
    observeCurrentHouseholdUseCase,
    observeChoresUseCase,
    observeMembersUseCase,
    observeCurrentDashboardUseCase,
    addChoreUseCase,
    updateChoreNameUseCase,
    updateChoreCategoryUseCase,
    updateChoreFrequencyUseCase,
    updateChoreActiveUseCase,
    deleteChoreUseCase,
    logCompletionUseCase,
    deleteCompletionUseCase,
)
