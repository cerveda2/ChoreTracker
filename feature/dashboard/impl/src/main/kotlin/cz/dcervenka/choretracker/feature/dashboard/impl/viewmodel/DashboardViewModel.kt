package cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.DeleteCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.DismissNotificationsBannerUseCase
import cz.dcervenka.choretracker.core.domain.usecase.LogCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
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
import cz.dcervenka.choretracker.core.domain.usecase.UpdateCompletionUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.error.AppError
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiIntent
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Instant

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeCurrentDashboardUseCase: ObserveCurrentDashboardUseCase,
    observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase,
    observeMembersUseCase: ObserveMembersUseCase,
    observeRecentCompletionsUseCase: ObserveRecentCompletionsUseCase,
    observeSyncStateUseCase: ObserveSyncStateUseCase,
    observeNotificationsBannerDismissedUseCase: ObserveNotificationsBannerDismissedUseCase,
    observeReminderSettingsUseCase: ObserveReminderSettingsUseCase,
    observeInviteNotificationsEnabledUseCase: ObserveInviteNotificationsEnabledUseCase,
    observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val logCompletionUseCase: LogCompletionUseCase,
    private val updateCompletionUseCase: UpdateCompletionUseCase,
    private val deleteCompletionUseCase: DeleteCompletionUseCase,
    private val retryPendingSyncUseCase: RetryPendingSyncUseCase,
    private val refreshHouseholdUseCase: RefreshHouseholdUseCase,
    private val dismissNotificationsBannerUseCase: DismissNotificationsBannerUseCase,
) : ViewModel() {

    private val _undoChannel = Channel<UndoEvent>(Channel.BUFFERED)
    val undoEvents: Flow<UndoEvent> = _undoChannel.receiveAsFlow()

    private val _errorChannel = Channel<AppError>(Channel.BUFFERED)
    val errorEvents: Flow<AppError> = _errorChannel.receiveAsFlow()

    private val isRefreshing = MutableStateFlow(false)

    // Null until the screen reports it, so the banner stays hidden rather than guessing.
    private val systemNotificationsEnabled = MutableStateFlow<Boolean?>(null)

    // Starts hidden instead of waiting for these reads, so the dashboard itself never waits on them.
    private val notificationsBanner: Flow<NotificationsBannerInputs> = combine(
        systemNotificationsEnabled,
        observeNotificationsBannerDismissedUseCase(),
        observeReminderSettingsUseCase(),
        observeInviteNotificationsEnabledUseCase(),
        observeAuthStateUseCase(),
    ) { systemEnabled, dismissed, reminderSettings, inviteNotificationsEnabled, authState ->
        NotificationsBannerInputs(
            blocked = systemEnabled == false &&
                !dismissed &&
                (authState as? AuthState.Authenticated)?.user?.isPreview != true,
            remindersEnabled = reminderSettings.enabled,
            inviteNotificationsEnabled = inviteNotificationsEnabled,
        )
    }.onStart { emit(NotificationsBannerInputs()) }

    // Assumed online until the screen reports otherwise, so the indicator doesn't open on "Offline".
    private val networkAvailable = MutableStateFlow(true)

    private val syncsRemotely: Flow<Boolean> = observeAuthStateUseCase()
        .map { (it as? AuthState.Authenticated)?.user?.isPreview == false }
        .onStart { emit(false) }

    val uiState: StateFlow<DashboardUiState> = observeCurrentHouseholdUseCase()
        .filterNotNull()
        .flatMapLatest { household ->
            combine(
                observeCurrentDashboardUseCase(),
                observeMembersUseCase(household.id),
                observeRecentCompletionsUseCase(household.id, limit = 25),
                observeSyncStateUseCase(household.id),
                isRefreshing,
            ) { snapshot, members, completions, syncState, refreshing ->
                val currentMember = members.find { it.isCurrentUser }
                DashboardUiState(
                    snapshot = snapshot,
                    members = members,
                    isManager = currentMember?.role?.isManager == true,
                    currentUserId = currentMember?.userId,
                    allCompletions = completions,
                    syncState = syncState,
                    isRefreshing = refreshing,
                )
            }
        }
        .combine(notificationsBanner) { state, banner ->
            // Only for notifications this user would actually get: invite-accepted pushes go to whoever
            // sent the invite, and only the owner and admins can.
            state.copy(
                showNotificationsBanner = banner.blocked &&
                    (banner.remindersEnabled || (banner.inviteNotificationsEnabled && state.isManager)),
            )
        }
        .combine(combine(networkAvailable, syncsRemotely, ::Pair)) { state, (online, syncsRemotely) ->
            val syncState = state.syncState
            state.copy(
                syncStatus = when {
                    !syncsRemotely -> null
                    // Offline uploads fail too; that's expected, so it isn't reported as a sync problem.
                    !online -> SyncStatus.OFFLINE
                    syncState != null && (syncState.pendingOperations > 0 || syncState.lastError != null) ->
                        SyncStatus.NOT_SYNCED
                    else -> SyncStatus.SYNCED
                },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    // uiState.allCompletions is capped at 25 for the dashboard's own 3-item preview - the
    // dedicated "see all" screen and the completion-detail lookup both need the full history
    // instead, so they observe this separately rather than sharing that capped list. Null (not
    // emptyList()) means "hasn't loaded yet", same convention as DashboardUiState.snapshot - a
    // household that genuinely has zero completions must not be mistaken for still-loading.
    val completionHistory: StateFlow<List<RecentCompletion>?> = observeCurrentHouseholdUseCase()
        .filterNotNull()
        .flatMapLatest { household -> observeRecentCompletionsUseCase(household.id, limit = Int.MAX_VALUE) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun dispatch(intent: DashboardUiIntent) {
        when (intent) {
            is DashboardUiIntent.LogCompletion -> logCompletion(
                householdId = intent.householdId,
                choreId = intent.choreId,
                participantIds = intent.participantIds,
                note = intent.note,
                completedAt = intent.completedAt,
            )
            is DashboardUiIntent.UpdateCompletion -> updateCompletion(
                intent.completionId,
                intent.note,
                intent.participantIds,
            )
            is DashboardUiIntent.DeleteCompletion -> deleteCompletion(intent.completionId)
            DashboardUiIntent.RetrySync -> retrySync()
            DashboardUiIntent.Refresh -> refresh()
            DashboardUiIntent.DismissNotificationsBanner -> viewModelScope.launch { dismissNotificationsBannerUseCase() }
            is DashboardUiIntent.SystemNotificationsChanged -> systemNotificationsEnabled.value = intent.enabled
            is DashboardUiIntent.NetworkAvailabilityChanged -> networkAvailable.value = intent.available
        }
    }

    private fun logCompletion(
        householdId: String,
        choreId: String,
        participantIds: List<String>,
        note: String?,
        completedAt: Instant? = null,
    ) {
        viewModelScope.launch {
            val result = logCompletionUseCase(
                householdId = householdId,
                choreId = choreId,
                participantMemberIds = participantIds,
                note = note,
                completedAt = completedAt,
            )
            when (result) {
                is AppResult.Success -> {
                    val choreName = uiState.value.snapshot?.activeChores
                        ?.find { it.id == choreId }?.name.orEmpty()
                    _undoChannel.send(UndoEvent(result.value, choreName))
                }
                is AppResult.Error -> _errorChannel.send(result.error)
            }
        }
    }

    private fun updateCompletion(completionId: String, note: String?, participantIds: List<String>) {
        viewModelScope.launch {
            val result = updateCompletionUseCase(completionId, note, participantIds)
            if (result is AppResult.Error) {
                _errorChannel.send(result.error)
            }
        }
    }

    private fun deleteCompletion(completionId: String) {
        viewModelScope.launch {
            val result = deleteCompletionUseCase(completionId)
            if (result is AppResult.Error) {
                _errorChannel.send(result.error)
            }
        }
    }

    private fun retrySync() {
        viewModelScope.launch {
            val result = retryPendingSyncUseCase()
            if (result is AppResult.Error) {
                _errorChannel.send(result.error)
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            refreshHouseholdUseCase()
            isRefreshing.value = false
        }
    }

    private data class NotificationsBannerInputs(
        val blocked: Boolean = false,
        val remindersEnabled: Boolean = false,
        val inviteNotificationsEnabled: Boolean = false,
    )
}
