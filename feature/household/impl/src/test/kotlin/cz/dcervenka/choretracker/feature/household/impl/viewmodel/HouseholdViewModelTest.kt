package cz.dcervenka.choretracker.feature.household.impl.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.CreateInviteUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveChoresUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveInvitesUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.test.mock.sampleChore
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import cz.dcervenka.choretracker.core.test.mock.sampleInvite
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

class HouseholdViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK
    lateinit var observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase

    @MockK
    lateinit var observeMembersUseCase: ObserveMembersUseCase

    @MockK
    lateinit var observeInvitesUseCase: ObserveInvitesUseCase

    @MockK
    lateinit var observeChoresUseCase: ObserveChoresUseCase

    @MockK
    lateinit var createInviteUseCase: CreateInviteUseCase

    private val householdFlow = MutableStateFlow<cz.dcervenka.choretracker.core.model.household.Household?>(null)
    private val membersFlow =
        MutableStateFlow(emptyList<cz.dcervenka.choretracker.core.model.household.HouseholdMember>())
    private val invitesFlow = MutableStateFlow(emptyList<cz.dcervenka.choretracker.core.model.household.Invite>())
    private val choresFlow = MutableStateFlow(emptyList<cz.dcervenka.choretracker.core.model.chore.Chore>())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        householdFlow.value = null
        membersFlow.value = emptyList()
        invitesFlow.value = emptyList()
        choresFlow.value = emptyList()
        every { observeCurrentHouseholdUseCase() } returns householdFlow
        every { observeMembersUseCase(any()) } returns membersFlow
        every { observeInvitesUseCase(any()) } returns invitesFlow
        every { observeChoresUseCase(any()) } returns choresFlow
        coEvery { createInviteUseCase(any()) } returns AppResult.Success(sampleInvite())
    }

    @Test
    fun `ui state combines household data`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().household).isNull()

            membersFlow.value = sampleMembers()
            invitesFlow.value = listOf(sampleInvite())
            choresFlow.value = listOf(sampleChore())
            householdFlow.value = sampleHousehold()
            val loadedState = awaitItem()
            assertThat(loadedState.members).hasSize(2)
            assertThat(loadedState.invites).hasSize(1)
            assertThat(loadedState.chores).hasSize(1)
        }
    }

    @Test
    fun `refreshInvite delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        householdFlow.value = sampleHousehold()

        viewModel.uiState.test {
            awaitItem()
            awaitItem()

            viewModel.refreshInvite()
            advanceUntilIdle()

            coVerify { createInviteUseCase("household-1") }
        }
    }
}

private fun HouseholdViewModelTest.createViewModel() = HouseholdViewModel(
    observeCurrentHouseholdUseCase = observeCurrentHouseholdUseCase,
    observeMembersUseCase = observeMembersUseCase,
    observeInvitesUseCase = observeInvitesUseCase,
    observeChoresUseCase = observeChoresUseCase,
    createInviteUseCase = createInviteUseCase,
)
