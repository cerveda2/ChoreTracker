package cz.dcervenka.choretracker.feature.settings.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveInviteNotificationsEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveReminderSettingsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetInviteNotificationsEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetReminderEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetReminderTimeUseCase
import cz.dcervenka.choretracker.feature.settings.impl.contract.NotificationSettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.NotificationSettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    observeReminderSettingsUseCase: ObserveReminderSettingsUseCase,
    observeInviteNotificationsEnabledUseCase: ObserveInviteNotificationsEnabledUseCase,
    observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase,
    observeMembersUseCase: ObserveMembersUseCase,
    private val setReminderEnabledUseCase: SetReminderEnabledUseCase,
    private val setReminderTimeUseCase: SetReminderTimeUseCase,
    private val setInviteNotificationsEnabledUseCase: SetInviteNotificationsEnabledUseCase,
) : ViewModel() {

    // Starts as allowed, so the owner never sees the switch flash disabled while members load.
    private val canInvite: Flow<Boolean> = observeCurrentHouseholdUseCase()
        .filterNotNull()
        .flatMapLatest { household -> observeMembersUseCase(household.id) }
        .map { members -> members.any { it.isCurrentUser && it.role.isManager } }
        .onStart { emit(true) }

    val uiState: StateFlow<NotificationSettingsUiState> = combine(
        observeReminderSettingsUseCase(),
        observeInviteNotificationsEnabledUseCase(),
        canInvite,
    ) { reminderSettings, inviteNotificationsEnabled, canInvite ->
        NotificationSettingsUiState(
            remindersEnabled = reminderSettings.enabled,
            hour = reminderSettings.hour,
            minute = reminderSettings.minute,
            inviteNotificationsEnabled = inviteNotificationsEnabled,
            canInvite = canInvite,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationSettingsUiState(),
    )

    fun dispatch(intent: NotificationSettingsUiIntent) {
        when (intent) {
            is NotificationSettingsUiIntent.SetRemindersEnabled -> setRemindersEnabled(intent.enabled)
            is NotificationSettingsUiIntent.SetTime -> setTime(intent.hour, intent.minute)
            is NotificationSettingsUiIntent.SetInviteNotificationsEnabled ->
                setInviteNotificationsEnabled(intent.enabled)
        }
    }

    private fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { setReminderEnabledUseCase(enabled) }
    }

    private fun setTime(hour: Int, minute: Int) {
        viewModelScope.launch { setReminderTimeUseCase(hour, minute) }
    }

    private fun setInviteNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { setInviteNotificationsEnabledUseCase(enabled) }
    }
}
