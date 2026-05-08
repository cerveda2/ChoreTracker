package cz.dcervenka.choretracker.feature.settings.impl.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.AddChoreUseCase
import cz.dcervenka.choretracker.core.domain.usecase.AddMemberUseCase
import cz.dcervenka.choretracker.core.domain.usecase.CreateInviteUseCase
import cz.dcervenka.choretracker.core.domain.usecase.DeleteChoreUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveChoresUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveInvitesUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SignOutUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreActiveUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreCategoryUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreFrequencyUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreNameUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateCurrentMemberDisplayNameUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateDisplayNameUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateHouseholdNameUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.test.mock.sampleAuthenticatedState
import cz.dcervenka.choretracker.core.test.mock.sampleChore
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import cz.dcervenka.choretracker.core.test.mock.sampleMembers
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiIntent
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

    @MockK
    lateinit var observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase

    @MockK
    lateinit var observeMembersUseCase: ObserveMembersUseCase

    @MockK
    lateinit var observeChoresUseCase: ObserveChoresUseCase

    @MockK
    lateinit var observeInvitesUseCase: ObserveInvitesUseCase

    @MockK
    lateinit var addMemberUseCase: AddMemberUseCase

    @MockK
    lateinit var addChoreUseCase: AddChoreUseCase

    @MockK
    lateinit var createInviteUseCase: CreateInviteUseCase

    @MockK
    lateinit var deleteChoreUseCase: DeleteChoreUseCase

    @MockK
    lateinit var updateDisplayNameUseCase: UpdateDisplayNameUseCase

    @MockK
    lateinit var updateCurrentMemberDisplayNameUseCase: UpdateCurrentMemberDisplayNameUseCase

    @MockK
    lateinit var updateChoreActiveUseCase: UpdateChoreActiveUseCase

    @MockK
    lateinit var updateChoreFrequencyUseCase: UpdateChoreFrequencyUseCase

    @MockK
    lateinit var updateChoreNameUseCase: UpdateChoreNameUseCase

    @MockK
    lateinit var updateChoreCategoryUseCase: UpdateChoreCategoryUseCase

    @MockK
    lateinit var updateHouseholdNameUseCase: UpdateHouseholdNameUseCase

    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private val householdFlow = MutableStateFlow<cz.dcervenka.choretracker.core.model.household.Household?>(null)
    private val membersFlow =
        MutableStateFlow(emptyList<cz.dcervenka.choretracker.core.model.household.HouseholdMember>())
    private val choresFlow = MutableStateFlow(emptyList<cz.dcervenka.choretracker.core.model.chore.Chore>())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        authStateFlow.value = AuthState.SignedOut
        householdFlow.value = null
        membersFlow.value = emptyList()
        choresFlow.value = emptyList()
        every { observeAuthStateUseCase() } returns authStateFlow
        every { observeCurrentHouseholdUseCase() } returns householdFlow
        every { observeMembersUseCase(any()) } answers { membersFlow }
        every { observeChoresUseCase(any()) } answers { choresFlow }
        every { observeInvitesUseCase(any()) } returns MutableStateFlow(emptyList())
        coEvery { signOutUseCase() } returns AppResult.Success(Unit)
        coEvery { addMemberUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { addChoreUseCase(any(), any(), any()) } returns AppResult.Success(Unit)
        coEvery { updateChoreCategoryUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery {
            createInviteUseCase(any())
        } returns AppResult.Success(cz.dcervenka.choretracker.core.test.mock.sampleInvite())
        coEvery { deleteChoreUseCase(any()) } returns AppResult.Success(Unit)
        coEvery { updateDisplayNameUseCase(any()) } returns AppResult.Success(Unit)
        coEvery { updateCurrentMemberDisplayNameUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { updateChoreActiveUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { updateChoreFrequencyUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { updateChoreNameUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { updateHouseholdNameUseCase(any(), any()) } returns AppResult.Success(Unit)
    }

    @Test
    fun `maps authenticated user into ui state`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        authStateFlow.value = sampleAuthenticatedState()
        householdFlow.value = sampleHousehold()
        membersFlow.value = sampleMembers()
        choresFlow.value = listOf(sampleChore())

        viewModel.uiState.test {
            assertThat(awaitItem().userLabel).isNull()

            val authenticated = awaitItem()
            assertThat(authenticated.userLabel).isEqualTo("Dana")
            assertThat(authenticated.userEmail).isEqualTo("dana@example.com")
            assertThat(authenticated.accountDisplayNameInput).isEqualTo("Dana")
            assertThat(authenticated.isSignedOut).isFalse()
            assertThat(authenticated.household?.name).isEqualTo("Home")
            assertThat(authenticated.members).hasSize(2)
            assertThat(authenticated.chores).hasSize(1)
        }
    }

    @Test
    fun `sign out delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.dispatch(SettingsUiIntent.SignOut)
        advanceUntilIdle()

        coVerify { signOutUseCase() }
    }

    @Test
    fun `saveAccountDisplayName updates auth and current member`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        val household = sampleHousehold()
        authStateFlow.value = sampleAuthenticatedState()
        householdFlow.value = household

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.dispatch(SettingsUiIntent.AccountDisplayNameChanged("Dana New"))
            viewModel.dispatch(SettingsUiIntent.SaveAccountDisplayName)
            advanceUntilIdle()

            coVerify { updateDisplayNameUseCase("Dana New") }
            coVerify { updateCurrentMemberDisplayNameUseCase(household.id, "Dana New") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateChoreName delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.dispatch(SettingsUiIntent.UpdateChoreName("chore-1", "Dishes"))
        advanceUntilIdle()

        coVerify { updateChoreNameUseCase("chore-1", "Dishes") }
    }

    @Test
    fun `updateChoreFrequency delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.dispatch(SettingsUiIntent.UpdateChoreFrequency("chore-1", 7))
        advanceUntilIdle()

        coVerify { updateChoreFrequencyUseCase("chore-1", 7) }
    }
}

private fun SettingsViewModelTest.createViewModel() = SettingsViewModel(
    observeAuthStateUseCase,
    observeCurrentHouseholdUseCase,
    observeMembersUseCase,
    observeChoresUseCase,
    observeInvitesUseCase,
    signOutUseCase,
    addMemberUseCase,
    addChoreUseCase,
    createInviteUseCase,
    deleteChoreUseCase,
    updateDisplayNameUseCase,
    updateCurrentMemberDisplayNameUseCase,
    updateChoreActiveUseCase,
    updateChoreFrequencyUseCase,
    updateChoreNameUseCase,
    updateChoreCategoryUseCase,
    updateHouseholdNameUseCase,
)
