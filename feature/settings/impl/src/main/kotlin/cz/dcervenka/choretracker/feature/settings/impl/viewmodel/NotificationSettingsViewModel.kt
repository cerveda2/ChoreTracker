package cz.dcervenka.choretracker.feature.settings.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.domain.usecase.ObserveReminderSettingsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetReminderEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetReminderTimeUseCase
import cz.dcervenka.choretracker.feature.settings.impl.contract.NotificationSettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.NotificationSettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    observeReminderSettingsUseCase: ObserveReminderSettingsUseCase,
    private val setReminderEnabledUseCase: SetReminderEnabledUseCase,
    private val setReminderTimeUseCase: SetReminderTimeUseCase,
) : ViewModel() {

    val uiState: StateFlow<NotificationSettingsUiState> = observeReminderSettingsUseCase()
        .map { settings ->
            NotificationSettingsUiState(
                enabled = settings.enabled,
                hour = settings.hour,
                minute = settings.minute,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationSettingsUiState(),
        )

    fun dispatch(intent: NotificationSettingsUiIntent) {
        when (intent) {
            is NotificationSettingsUiIntent.SetEnabled -> setEnabled(intent.enabled)
            is NotificationSettingsUiIntent.SetTime -> setTime(intent.hour, intent.minute)
        }
    }

    private fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { setReminderEnabledUseCase(enabled) }
    }

    private fun setTime(hour: Int, minute: Int) {
        viewModelScope.launch { setReminderTimeUseCase(hour, minute) }
    }
}
