package cz.dcervenka.choretracker.feature.household.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.model.Chore
import cz.dcervenka.choretracker.core.model.Household
import cz.dcervenka.choretracker.core.model.HouseholdMember
import cz.dcervenka.choretracker.core.model.Invite
import cz.dcervenka.choretracker.feature.household.api.HOUSEHOLD_ROUTE
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HouseholdUiState(
    val household: Household? = null,
    val members: List<HouseholdMember> = emptyList(),
    val chores: List<Chore> = emptyList(),
    val invites: List<Invite> = emptyList(),
    val memberInput: String = "",
    val choreInput: String = "",
)

@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val choreRepository: ChoreRepository,
) : ViewModel() {
    private val memberInput = MutableStateFlow("")
    private val choreInput = MutableStateFlow("")

    val uiState: StateFlow<HouseholdUiState> = householdRepository.observeCurrentHousehold()
        .filterNotNull()
        .flatMapLatest { household ->
            combine(
                householdRepository.observeMembers(household.id),
                householdRepository.observeInvites(household.id),
                choreRepository.observeChores(household.id),
                memberInput,
                choreInput,
            ) { members, invites, chores, currentMember, currentChore ->
                HouseholdUiState(
                    household = household,
                    members = members,
                    chores = chores,
                    invites = invites,
                    memberInput = currentMember,
                    choreInput = currentChore,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HouseholdUiState(),
        )

    fun onMemberInputChange(value: String) {
        memberInput.value = value
    }

    fun onChoreInputChange(value: String) {
        choreInput.value = value
    }

    fun addMember() {
        val state = uiState.value
        val household = state.household ?: return
        viewModelScope.launch {
            householdRepository.addMember(household.id, state.memberInput)
            memberInput.value = ""
        }
    }

    fun addChore() {
        val state = uiState.value
        val household = state.household ?: return
        viewModelScope.launch {
            choreRepository.addChore(household.id, state.choreInput)
            choreInput.value = ""
        }
    }

    fun refreshInvite() {
        uiState.value.household?.let { household ->
            viewModelScope.launch { householdRepository.createInvite(household.id) }
        }
    }

    fun updateChoreActive(choreId: String, isActive: Boolean) {
        viewModelScope.launch {
            choreRepository.updateChoreActive(choreId, isActive)
        }
    }
}

fun NavGraphBuilder.householdScreen() {
    composable(route = HOUSEHOLD_ROUTE) {
        val viewModel: HouseholdViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val spacing = LocalSpacing.current

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            item {
                Column(modifier = Modifier.padding(spacing.large)) {
                    Text("Household", style = MaterialTheme.typography.headlineMedium)
                    Text(uiState.household?.inviteCode?.let { "Active invite: $it" } ?: "No household yet")
                    Button(onClick = viewModel::refreshInvite) {
                        Text("Refresh invite")
                    }
                }
            }
            item {
                Card(modifier = Modifier.padding(horizontal = spacing.large)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        Text("Members", style = MaterialTheme.typography.titleLarge)
                        uiState.members.forEach { member ->
                            Text("${member.displayName} • ${member.role.name.lowercase()}")
                        }
                        OutlinedTextField(
                            value = uiState.memberInput,
                            onValueChange = viewModel::onMemberInputChange,
                            label = { Text("New member") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = viewModel::addMember, modifier = Modifier.fillMaxWidth()) {
                            Text("Add member")
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.padding(horizontal = spacing.large)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        Text("Chores", style = MaterialTheme.typography.titleLarge)
                        uiState.chores.forEach { chore ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(chore.name)
                                Switch(
                                    checked = chore.isActive,
                                    onCheckedChange = { checked -> viewModel.updateChoreActive(chore.id, checked) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = uiState.choreInput,
                            onValueChange = viewModel::onChoreInputChange,
                            label = { Text("New chore") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = viewModel::addChore, modifier = Modifier.fillMaxWidth()) {
                            Text("Add chore")
                        }
                    }
                }
            }
        }
    }
}
