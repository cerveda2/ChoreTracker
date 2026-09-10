package cz.dcervenka.choretracker.feature.settings.impl.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.AddMemberUseCase
import cz.dcervenka.choretracker.core.domain.usecase.CreateInviteUseCase
import cz.dcervenka.choretracker.core.domain.usecase.CreateMemberInviteUseCase
import cz.dcervenka.choretracker.core.domain.usecase.LeaveHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveInvitesUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveReminderSettingsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveThemeSettingsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.RemoveMemberUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetDynamicColorUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetThemeModeUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SignOutUseCase
import cz.dcervenka.choretracker.core.domain.usecase.TransferOwnershipUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateCurrentMemberDisplayNameUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateDisplayNameUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateHouseholdNameUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import cz.dcervenka.choretracker.core.model.settings.ThemeMode
import cz.dcervenka.choretracker.core.model.settings.ThemeSettings
import cz.dcervenka.choretracker.core.test.mock.sampleAuthenticatedState
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import cz.dcervenka.choretracker.core.test.mock.sampleInvite
import cz.dcervenka.choretracker.core.test.mock.sampleMembers
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiEvent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiIntent
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
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
    lateinit var observeInvitesUseCase: ObserveInvitesUseCase

    @MockK
    lateinit var addMemberUseCase: AddMemberUseCase

    @MockK
    lateinit var createInviteUseCase: CreateInviteUseCase

    @MockK
    lateinit var createMemberInviteUseCase: CreateMemberInviteUseCase

    @MockK
    lateinit var removeMemberUseCase: RemoveMemberUseCase

    @MockK
    lateinit var transferOwnershipUseCase: TransferOwnershipUseCase

    @MockK
    lateinit var leaveHouseholdUseCase: LeaveHouseholdUseCase

    @MockK
    lateinit var updateDisplayNameUseCase: UpdateDisplayNameUseCase

    @MockK
    lateinit var updateCurrentMemberDisplayNameUseCase: UpdateCurrentMemberDisplayNameUseCase

    @MockK
    lateinit var updateHouseholdNameUseCase: UpdateHouseholdNameUseCase

    @MockK
    lateinit var observeThemeSettingsUseCase: ObserveThemeSettingsUseCase

    @MockK
    lateinit var observeReminderSettingsUseCase: ObserveReminderSettingsUseCase

    @MockK
    lateinit var setThemeModeUseCase: SetThemeModeUseCase

    @MockK
    lateinit var setDynamicColorUseCase: SetDynamicColorUseCase

    private val themeSettingsFlow = MutableStateFlow(ThemeSettings())
    private val reminderSettingsFlow = MutableStateFlow(ReminderSettings())
    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private val householdFlow = MutableStateFlow<cz.dcervenka.choretracker.core.model.household.Household?>(null)
    private val membersFlow =
        MutableStateFlow(emptyList<cz.dcervenka.choretracker.core.model.household.HouseholdMember>())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        authStateFlow.value = AuthState.SignedOut
        householdFlow.value = null
        membersFlow.value = emptyList()
        every { observeAuthStateUseCase() } returns authStateFlow
        every { observeCurrentHouseholdUseCase() } returns householdFlow
        every { observeMembersUseCase(any()) } answers { membersFlow }
        every { observeInvitesUseCase(any()) } returns MutableStateFlow(emptyList())
        every { observeThemeSettingsUseCase() } returns themeSettingsFlow
        every { observeReminderSettingsUseCase() } returns reminderSettingsFlow
        coEvery { setThemeModeUseCase(any()) } just Runs
        coEvery { setDynamicColorUseCase(any()) } just Runs
        coEvery { signOutUseCase() } returns AppResult.Success(Unit)
        coEvery { addMemberUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { createInviteUseCase(any()) } returns AppResult.Success(sampleInvite())
        coEvery { removeMemberUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { updateDisplayNameUseCase(any()) } returns AppResult.Success(Unit)
        coEvery { updateCurrentMemberDisplayNameUseCase(any(), any()) } returns AppResult.Success(Unit)
        coEvery { updateHouseholdNameUseCase(any(), any()) } returns AppResult.Success(Unit)
    }

    @Test
    fun `maps authenticated user into ui state`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        authStateFlow.value = sampleAuthenticatedState()
        householdFlow.value = sampleHousehold()
        membersFlow.value = sampleMembers()

        viewModel.uiState.test {
            assertThat(awaitItem().userLabel).isNull()

            val authenticated = awaitItem()
            assertThat(authenticated.userLabel).isEqualTo("Dana")
            assertThat(authenticated.userEmail).isEqualTo("dana@example.com")
            assertThat(authenticated.accountDisplayNameInput).isEqualTo("Dana")
            assertThat(authenticated.isSignedOut).isFalse()
            assertThat(authenticated.household?.name).isEqualTo("Home")
            assertThat(authenticated.members).hasSize(2)
        }
    }

    @Test
    fun `clearing the display name field does not snap back to the resolved name`() =
        runTest(coroutineRule.dispatcher) {
            val viewModel = createViewModel()
            authStateFlow.value = sampleAuthenticatedState()
            householdFlow.value = sampleHousehold()
            membersFlow.value = sampleMembers()

            viewModel.uiState.test {
                awaitItem()
                val hydrated = awaitItem()
                assertThat(hydrated.accountDisplayNameInput).isEqualTo("Dana")

                viewModel.dispatch(SettingsUiIntent.AccountDisplayNameChanged(""))

                assertThat(awaitItem().accountDisplayNameInput).isEmpty()
                cancelAndIgnoreRemainingEvents()
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
    fun `uiState reflects theme and reminder settings`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        themeSettingsFlow.value = ThemeSettings(mode = ThemeMode.DARK, dynamicColor = true)
        reminderSettingsFlow.value = ReminderSettings(enabled = true, hour = 8, minute = 30)

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            assertThat(state.themeSettings).isEqualTo(ThemeSettings(mode = ThemeMode.DARK, dynamicColor = true))
            assertThat(state.reminderSettings).isEqualTo(ReminderSettings(enabled = true, hour = 8, minute = 30))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SetThemeMode delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.dispatch(SettingsUiIntent.SetThemeMode(ThemeMode.DARK))
        advanceUntilIdle()

        coVerify { setThemeModeUseCase(ThemeMode.DARK) }
    }

    @Test
    fun `SetDynamicColor delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.dispatch(SettingsUiIntent.SetDynamicColor(true))
        advanceUntilIdle()

        coVerify { setDynamicColorUseCase(true) }
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
    fun `TransferOwnership delegates to the use case and emits OwnershipTransferred`() =
        runTest(coroutineRule.dispatcher) {
            coEvery { transferOwnershipUseCase(any(), any()) } returns AppResult.Success(Unit)
            val viewModel = createViewModel()
            authStateFlow.value = sampleAuthenticatedState()
            householdFlow.value = sampleHousehold()

            viewModel.uiState.test {
                advanceUntilIdle()
                viewModel.events.test {
                    viewModel.dispatch(SettingsUiIntent.TransferOwnership("member-2"))
                    advanceUntilIdle()

                    coVerify { transferOwnershipUseCase("household-1", "member-2") }
                    assertThat(awaitItem()).isEqualTo(SettingsUiEvent.OwnershipTransferred)
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `LeaveHousehold delegates to the use case and surfaces an error when it fails`() =
        runTest(coroutineRule.dispatcher) {
            coEvery { leaveHouseholdUseCase(any()) } returns AppResult.Error("Connect to the internet to leave.")
            val viewModel = createViewModel()
            authStateFlow.value = sampleAuthenticatedState()
            householdFlow.value = sampleHousehold()

            viewModel.uiState.test {
                advanceUntilIdle()
                viewModel.events.test {
                    viewModel.dispatch(SettingsUiIntent.LeaveHousehold)
                    advanceUntilIdle()

                    coVerify { leaveHouseholdUseCase("household-1") }
                    assertThat(awaitItem()).isEqualTo(SettingsUiEvent.Error("Connect to the internet to leave."))
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private fun SettingsViewModelTest.createViewModel() = SettingsViewModel(
    observeAuthStateUseCase,
    observeCurrentHouseholdUseCase,
    observeMembersUseCase,
    observeInvitesUseCase,
    observeThemeSettingsUseCase,
    observeReminderSettingsUseCase,
    signOutUseCase,
    addMemberUseCase,
    createInviteUseCase,
    createMemberInviteUseCase,
    removeMemberUseCase,
    transferOwnershipUseCase,
    leaveHouseholdUseCase,
    updateDisplayNameUseCase,
    updateCurrentMemberDisplayNameUseCase,
    updateHouseholdNameUseCase,
    setThemeModeUseCase,
    setDynamicColorUseCase,
)
