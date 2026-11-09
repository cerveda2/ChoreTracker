package cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.CancelChoreRequestUseCase
import cz.dcervenka.choretracker.core.domain.usecase.DeclineChoreRequestUseCase
import cz.dcervenka.choretracker.core.domain.usecase.DeleteCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.DismissNotificationsBannerUseCase
import cz.dcervenka.choretracker.core.domain.usecase.LogCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveChoreRequestsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentDashboardUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveInviteNotificationsEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveNotificationsBannerDismissedUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveRecentCompletionsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveReminderSettingsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveSyncStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.RefreshHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.RetryPendingSyncUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SendChoreRequestsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateCompletionUseCase
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreRequestItem
import cz.dcervenka.choretracker.core.model.chore.ChoreRequests
import cz.dcervenka.choretracker.core.model.error.AppError
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.core.model.stats.ChoreTurn
import cz.dcervenka.choretracker.core.model.sync.SyncState
import cz.dcervenka.choretracker.core.test.mock.sampleChore
import cz.dcervenka.choretracker.core.test.mock.sampleDashboardSnapshot
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import cz.dcervenka.choretracker.core.test.mock.sampleMembers
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiIntent
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.Nudge
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.SyncStatus
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
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
    lateinit var observeNotificationsBannerDismissedUseCase: ObserveNotificationsBannerDismissedUseCase

    @MockK
    lateinit var observeReminderSettingsUseCase: ObserveReminderSettingsUseCase

    @MockK
    lateinit var observeInviteNotificationsEnabledUseCase: ObserveInviteNotificationsEnabledUseCase

    @MockK
    lateinit var observeAuthStateUseCase: ObserveAuthStateUseCase

    @MockK
    lateinit var logCompletionUseCase: LogCompletionUseCase

    @MockK
    lateinit var updateCompletionUseCase: UpdateCompletionUseCase

    @MockK
    lateinit var deleteCompletionUseCase: DeleteCompletionUseCase

    @MockK
    lateinit var retryPendingSyncUseCase: RetryPendingSyncUseCase

    @MockK
    lateinit var refreshHouseholdUseCase: RefreshHouseholdUseCase

    @MockK
    lateinit var dismissNotificationsBannerUseCase: DismissNotificationsBannerUseCase

    @MockK
    lateinit var observeChoreRequestsUseCase: ObserveChoreRequestsUseCase

    @MockK
    lateinit var declineChoreRequestUseCase: DeclineChoreRequestUseCase

    @MockK
    lateinit var cancelChoreRequestUseCase: CancelChoreRequestUseCase

    @MockK
    lateinit var sendChoreRequestsUseCase: SendChoreRequestsUseCase

    private val requestsFlow = MutableStateFlow(ChoreRequests())

    private val dashboardFlow = MutableStateFlow(sampleDashboardSnapshot())
    private val householdFlow = MutableStateFlow<Household?>(null)
    private val membersFlow = MutableStateFlow(emptyList<HouseholdMember>())
    private val completionsFlow = MutableStateFlow(sampleDashboardSnapshot().recentCompletions)
    private val syncStateFlow = MutableStateFlow<SyncState?>(null)
    private val bannerDismissedFlow = MutableStateFlow(false)
    private val reminderSettingsFlow = MutableStateFlow(ReminderSettings())
    private val inviteNotificationsEnabledFlow = MutableStateFlow(true)
    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.Authenticated(sampleUser()))

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        dashboardFlow.value = sampleDashboardSnapshot()
        householdFlow.value = null
        membersFlow.value = emptyList()
        bannerDismissedFlow.value = false
        reminderSettingsFlow.value = ReminderSettings()
        inviteNotificationsEnabledFlow.value = true
        authStateFlow.value = AuthState.Authenticated(sampleUser())
        every { observeCurrentDashboardUseCase() } returns dashboardFlow
        every { observeCurrentHouseholdUseCase() } returns householdFlow
        every { observeMembersUseCase(any()) } answers { membersFlow }
        every { observeRecentCompletionsUseCase(any(), any()) } answers { completionsFlow }
        every { observeSyncStateUseCase(any()) } answers { syncStateFlow }
        every { observeNotificationsBannerDismissedUseCase() } returns bannerDismissedFlow
        every { observeReminderSettingsUseCase() } returns reminderSettingsFlow
        every { observeInviteNotificationsEnabledUseCase() } returns inviteNotificationsEnabledFlow
        every { observeAuthStateUseCase() } returns authStateFlow
        coEvery { logCompletionUseCase(any(), any(), any(), any()) } returns AppResult.Success("completion-id")
        coEvery { updateCompletionUseCase(any(), any(), any()) } returns AppResult.Success(Unit)
        coEvery { deleteCompletionUseCase(any()) } returns AppResult.Success(Unit)
        coEvery { retryPendingSyncUseCase() } returns AppResult.Success(Unit)
        coEvery { refreshHouseholdUseCase() } returns AppResult.Success(Unit)
        coEvery { dismissNotificationsBannerUseCase() } coAnswers { bannerDismissedFlow.value = true }
        requestsFlow.value = ChoreRequests()
        every { observeChoreRequestsUseCase(any()) } returns requestsFlow
        coEvery { sendChoreRequestsUseCase(any(), any(), any(), any()) } returns AppResult.Success(Unit)
        coEvery { declineChoreRequestUseCase(any()) } returns AppResult.Success(Unit)
        coEvery { cancelChoreRequestUseCase(any()) } returns AppResult.Success(Unit)
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
                lastError = AppError.PERMISSION_DENIED,
            )

            val state = awaitItem()
            assertThat(state.syncState?.pendingOperations).isEqualTo(2)
            assertThat(state.syncState?.lastError).isEqualTo(AppError.PERMISSION_DENIED)
        }
    }

    @Test
    fun `sync status reports offline before any sync problem`() = runTest(coroutineRule.dispatcher) {
        householdFlow.value = sampleHousehold()
        dashboardFlow.value = sampleDashboardSnapshot()
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.syncStatus).isEqualTo(SyncStatus.SYNCED)

        syncStateFlow.value = SyncState(
            householdId = "household-1",
            lastSyncedAt = null,
            lastSyncAttemptAt = Instant.parse("2026-04-10T05:24:20Z"),
            pendingOperations = 1,
            lastError = AppError.UNKNOWN,
        )
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.syncStatus).isEqualTo(SyncStatus.NOT_SYNCED)

        viewModel.dispatch(DashboardUiIntent.NetworkAvailabilityChanged(available = false))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.syncStatus).isEqualTo(SyncStatus.OFFLINE)

        viewModel.dispatch(DashboardUiIntent.NetworkAvailabilityChanged(available = true))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.syncStatus).isEqualTo(SyncStatus.NOT_SYNCED)
    }

    @Test
    fun `sync status is hidden in preview mode`() = runTest(coroutineRule.dispatcher) {
        householdFlow.value = sampleHousehold()
        dashboardFlow.value = sampleDashboardSnapshot()
        authStateFlow.value = AuthState.Authenticated(sampleUser().copy(isPreview = true))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.syncStatus).isNull()
    }

    @Test
    fun `completionHistory observes the full history, uncapped, independent of uiState`() =
        runTest(coroutineRule.dispatcher) {
            val household = sampleHousehold()
            val viewModel = createViewModel()

            viewModel.completionHistory.test {
                assertThat(awaitItem()).isNull()

                householdFlow.value = household

                assertThat(awaitItem()).isEqualTo(completionsFlow.value)
            }

            verify { observeRecentCompletionsUseCase(household.id, limit = Int.MAX_VALUE) }
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
    fun `retry sync emits an error event when the use case fails`() = runTest(coroutineRule.dispatcher) {
        coEvery { retryPendingSyncUseCase() } returns AppResult.Error(AppError.NETWORK)
        val viewModel = createViewModel()

        viewModel.errorEvents.test {
            viewModel.dispatch(DashboardUiIntent.RetrySync)
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(AppError.NETWORK)
        }
    }

    @Test
    fun `refresh delegates to use case and toggles isRefreshing`() = runTest(coroutineRule.dispatcher) {
        householdFlow.value = sampleHousehold()
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.dispatch(DashboardUiIntent.Refresh)
            advanceUntilIdle()

            coVerify { refreshHouseholdUseCase() }
            assertThat(viewModel.uiState.value.isRefreshing).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
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

    @Test
    fun `log completion emits an error event when the use case fails`() = runTest(coroutineRule.dispatcher) {
        coEvery { logCompletionUseCase(any(), any(), any(), any()) } returns
            AppResult.Error(AppError.PARTICIPANT_REQUIRED)
        val viewModel = createViewModel()

        viewModel.errorEvents.test {
            viewModel.dispatch(
                DashboardUiIntent.LogCompletion(
                    householdId = "household-1",
                    choreId = "chore-1",
                    participantIds = emptyList(),
                    note = null,
                    completedAt = null,
                )
            )
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(AppError.PARTICIPANT_REQUIRED)
        }
    }

    @Test
    fun `update completion emits an error event when the use case fails`() = runTest(coroutineRule.dispatcher) {
        coEvery { updateCompletionUseCase(any(), any(), any()) } returns
            AppResult.Error(AppError.COMPLETION_EDIT_FORBIDDEN)
        val viewModel = createViewModel()

        viewModel.errorEvents.test {
            viewModel.dispatch(DashboardUiIntent.UpdateCompletion("completion-1", "Note", listOf("member-1")))
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(AppError.COMPLETION_EDIT_FORBIDDEN)
        }
    }

    @Test
    fun `delete completion emits an error event when the use case fails`() = runTest(coroutineRule.dispatcher) {
        coEvery { deleteCompletionUseCase(any()) } returns
            AppResult.Error(AppError.COMPLETION_DELETE_FORBIDDEN)
        val viewModel = createViewModel()

        viewModel.errorEvents.test {
            viewModel.dispatch(DashboardUiIntent.DeleteCompletion("completion-1"))
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(AppError.COMPLETION_DELETE_FORBIDDEN)
        }
    }

    @Test
    fun `update completion emits no error event when the use case succeeds`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.errorEvents.test {
            viewModel.dispatch(DashboardUiIntent.UpdateCompletion("completion-1", "Note", listOf("member-1")))
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun `notifications banner stays hidden until the screen reports notifications are off`() =
        runTest(coroutineRule.dispatcher) {
            householdFlow.value = sampleHousehold()
            membersFlow.value = sampleMembers()
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.showNotificationsBanner).isFalse()

            viewModel.dispatch(DashboardUiIntent.SystemNotificationsChanged(enabled = false))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.showNotificationsBanner).isTrue()

            viewModel.dispatch(DashboardUiIntent.SystemNotificationsChanged(enabled = true))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.showNotificationsBanner).isFalse()
        }

    @Test
    fun `dismissing the notifications banner persists it and hides it`() = runTest(coroutineRule.dispatcher) {
        householdFlow.value = sampleHousehold()
        membersFlow.value = sampleMembers()
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.dispatch(DashboardUiIntent.SystemNotificationsChanged(enabled = false))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showNotificationsBanner).isTrue()

        viewModel.dispatch(DashboardUiIntent.DismissNotificationsBanner)
        advanceUntilIdle()

        coVerify { dismissNotificationsBannerUseCase() }
        assertThat(viewModel.uiState.value.showNotificationsBanner).isFalse()
    }

    @Test
    fun `ui state exposes the current user's id and whether they manage the household`() =
        runTest(coroutineRule.dispatcher) {
            householdFlow.value = sampleHousehold()
            dashboardFlow.value = sampleDashboardSnapshot()
            membersFlow.value = sampleMembers()
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isManager).isTrue()
            assertThat(viewModel.uiState.value.currentUserId).isEqualTo("user-1")

            membersFlow.value = sampleMembers().map { it.copy(isCurrentUser = it.id == "member-2") }
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isManager).isFalse()
            assertThat(viewModel.uiState.value.currentUserId).isEqualTo("user-2")

            membersFlow.value = sampleMembers().map {
                it.copy(isCurrentUser = it.id == "member-2", role = if (it.id == "member-2") HouseholdRole.ADMIN else it.role)
            }
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isManager).isTrue()
        }

    @Test
    fun `notifications banner only shows for notifications the user would get`() =
        runTest(coroutineRule.dispatcher) {
            householdFlow.value = sampleHousehold()
            // Invite-accepted pushes only go to the owner, so a member with reminders off gets nothing.
            membersFlow.value = sampleMembers().map { it.copy(isCurrentUser = it.id == "member-2") }
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            viewModel.dispatch(DashboardUiIntent.SystemNotificationsChanged(enabled = false))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.showNotificationsBanner).isFalse()

            reminderSettingsFlow.value = ReminderSettings(enabled = true)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.showNotificationsBanner).isTrue()
        }

    @Test
    fun `notifications banner counts invite pushes for an admin, who can invite too`() =
        runTest(coroutineRule.dispatcher) {
            householdFlow.value = sampleHousehold()
            membersFlow.value = sampleMembers().map {
                it.copy(isCurrentUser = it.id == "member-2", role = HouseholdRole.ADMIN)
            }
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            viewModel.dispatch(DashboardUiIntent.SystemNotificationsChanged(enabled = false))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showNotificationsBanner).isTrue()
        }

    @Test
    fun `notifications banner never shows in preview mode`() = runTest(coroutineRule.dispatcher) {
        householdFlow.value = sampleHousehold()
        membersFlow.value = sampleMembers()
        authStateFlow.value = AuthState.Authenticated(sampleUser().copy(isPreview = true))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.dispatch(DashboardUiIntent.SystemNotificationsChanged(enabled = false))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showNotificationsBanner).isFalse()
    }

    @Test
    fun `declining and cancelling go to their use cases and report a failure`() = runTest(coroutineRule.dispatcher) {
        coEvery { cancelChoreRequestUseCase("request-1") } returns AppResult.Error(AppError.PERMISSION_DENIED)
        val viewModel = createViewModel()
        val errors = mutableListOf<AppError>()
        val errorsJob = launch { viewModel.errorEvents.collect { errors += it } }

        viewModel.dispatch(DashboardUiIntent.DeclineRequest("request-2"))
        viewModel.dispatch(DashboardUiIntent.CancelRequest("request-1"))
        advanceUntilIdle()

        coVerify { declineChoreRequestUseCase("request-2") }
        assertThat(errors).containsExactly(AppError.PERMISSION_DENIED)
        errorsJob.cancel()
    }

    @Test
    fun `a due chore on someone else's turn can be reminded about, once`() = runTest(coroutineRule.dispatcher) {
        householdFlow.value = sampleHousehold()
        membersFlow.value = sampleMembers()
        dashboardFlow.value = sampleDashboardSnapshot().copy(
            staleChores = listOf(
                ChoreStaleness(
                    choreId = "chore-1",
                    choreName = "Kitchen",
                    lastCompletedDate = null,
                    daysSinceLastCompletion = null,
                    frequencyDays = null,
                    status = ChoreStatus.NEVER,
                    turn = ChoreTurn(memberId = "member-2", memberName = "Alex", isCurrentUser = false),
                ),
            ),
        )
        val viewModel = createViewModel()
        val stateJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.nudges["chore-1"])
            .isEqualTo(Nudge(memberId = "member-2", memberName = "Alex", alreadyAsked = false))
        viewModel.dispatch(DashboardUiIntent.Remind("chore-1"))
        advanceUntilIdle()
        coVerify { sendChoreRequestsUseCase("household-1", "member-2", listOf("chore-1"), null) }

        requestsFlow.value = ChoreRequests(
            waitingOn = listOf(
                ChoreRequestItem(
                    id = "request-1",
                    choreId = "chore-1",
                    choreName = "Kitchen",
                    category = ChoreCategory.OTHER,
                    otherMemberId = "member-2",
                    otherMemberName = "Alex",
                    note = null,
                    createdAt = Instant.parse("2026-01-05T10:00:00Z"),
                ),
            ),
        )
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.nudges["chore-1"]?.alreadyAsked).isTrue()
        stateJob.cancel()
    }
}

private fun DashboardViewModelTest.createViewModel() = DashboardViewModel(
    observeCurrentDashboardUseCase = observeCurrentDashboardUseCase,
    observeCurrentHouseholdUseCase = observeCurrentHouseholdUseCase,
    observeMembersUseCase = observeMembersUseCase,
    observeRecentCompletionsUseCase = observeRecentCompletionsUseCase,
    observeSyncStateUseCase = observeSyncStateUseCase,
    observeNotificationsBannerDismissedUseCase = observeNotificationsBannerDismissedUseCase,
    observeReminderSettingsUseCase = observeReminderSettingsUseCase,
    observeInviteNotificationsEnabledUseCase = observeInviteNotificationsEnabledUseCase,
    observeAuthStateUseCase = observeAuthStateUseCase,
    observeChoreRequestsUseCase = observeChoreRequestsUseCase,
    logCompletionUseCase = logCompletionUseCase,
    updateCompletionUseCase = updateCompletionUseCase,
    deleteCompletionUseCase = deleteCompletionUseCase,
    retryPendingSyncUseCase = retryPendingSyncUseCase,
    refreshHouseholdUseCase = refreshHouseholdUseCase,
    dismissNotificationsBannerUseCase = dismissNotificationsBannerUseCase,
    declineChoreRequestUseCase = declineChoreRequestUseCase,
    cancelChoreRequestUseCase = cancelChoreRequestUseCase,
    sendChoreRequestsUseCase = sendChoreRequestsUseCase,
)

private fun sampleUser() = AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana")
