package cz.dcervenka.choretracker.feature.settings.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.domain.usecase.ObserveInviteNotificationsEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveReminderSettingsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetInviteNotificationsEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetReminderEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetReminderTimeUseCase
import cz.dcervenka.choretracker.feature.settings.impl.contract.NotificationSettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.NotificationSettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    observeReminderSettingsUseCase: ObserveReminderSettingsUseCase,
    observeInviteNotificationsEnabledUseCase: ObserveInviteNotificationsEnabledUseCase,
    private val setReminderEnabledUseCase: SetReminderEnabledUseCase,
    private val setReminderTimeUseCase: SetReminderTimeUseCase,
    private val setInviteNotificationsEnabledUseCase: SetInviteNotificationsEnabledUseCase,
) : ViewModel() {

    val uiState: StateFlow<NotificationSettingsUiState> = combine(
        observeReminderSettingsUseCase(),
        observeInviteNotificationsEnabledUseCase(),
    ) { reminderSettings, inviteNotificationsEnabled ->
        NotificationSettingsUiState(
            remindersEnabled = reminderSettings.enabled,
            hour = reminderSettings.hour,
            minute = reminderSettings.minute,
            inviteNotificationsEnabled = inviteNotificationsEnabled,
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
