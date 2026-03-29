package cz.dcervenka.choretracker.feature.onboarding.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.design.components.SecondaryButton
import cz.dcervenka.choretracker.feature.onboarding.api.ONBOARDING_ROUTE
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val householdName: String = "",
    val displayName: String = "",
    val inviteCode: String = "",
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
) : ViewModel() {
    private val householdName = MutableStateFlow("")
    private val displayName = MutableStateFlow("")
    private val inviteCode = MutableStateFlow("")
    private val isWorking = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OnboardingUiState> = combine(
        householdName,
        displayName,
        inviteCode,
        isWorking,
        errorMessage,
    ) { currentHousehold, currentDisplayName, currentInvite, working, error ->
        OnboardingUiState(
            householdName = currentHousehold,
            displayName = currentDisplayName,
            inviteCode = currentInvite,
            isWorking = working,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OnboardingUiState(),
    )

    fun onHouseholdNameChange(value: String) {
        householdName.value = value
    }

    fun onDisplayNameChange(value: String) {
        displayName.value = value
    }

    fun onInviteCodeChange(value: String) {
        inviteCode.value = value
    }

    fun createHousehold() {
        submit {
            householdRepository.createHousehold(
                name = householdName.value,
                ownerDisplayName = displayName.value,
            )
        }
    }

    fun joinHousehold() {
        submit {
            householdRepository.joinHousehold(
                code = inviteCode.value,
                currentUserDisplayName = displayName.value,
            )
        }
    }

    private fun submit(block: suspend () -> AppResult<*>) {
        viewModelScope.launch {
            isWorking.value = true
            errorMessage.value = null
            when (val result = block()) {
                is AppResult.Error -> errorMessage.value = result.message
                is AppResult.Success -> Unit
            }
            isWorking.value = false
        }
    }
}

fun NavGraphBuilder.onboardingScreen() {
    composable(route = ONBOARDING_ROUTE) {
        val viewModel: OnboardingViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val spacing = LocalSpacing.current

        ChoreScaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(spacing.large),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                ScreenHeader(
                    title = "Set up your household",
                    subtitle = "Create a fresh household or join one with an invite code. The local Room database is already the source of truth.",
                )
                uiState.errorMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = { Text("Your name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.householdName,
                    onValueChange = viewModel::onHouseholdNameChange,
                    label = { Text("Household name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = "Create household",
                    onClick = viewModel::createHousehold,
                    enabled = !uiState.isWorking,
                )
                OutlinedTextField(
                    value = uiState.inviteCode,
                    onValueChange = viewModel::onInviteCodeChange,
                    label = { Text("Invite code") },
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    text = "Join household",
                    onClick = viewModel::joinHousehold,
                    enabled = !uiState.isWorking,
                )
            }
        }
    }
}
