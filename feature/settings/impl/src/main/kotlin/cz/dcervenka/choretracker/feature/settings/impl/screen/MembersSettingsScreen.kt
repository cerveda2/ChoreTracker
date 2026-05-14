package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiEvent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState
import kotlinx.coroutines.flow.Flow

@Composable
fun MembersSettingsScreen(
    uiState: SettingsUiState,
    events: Flow<SettingsUiEvent>,
    onBack: () -> Unit,
    onIntent: (SettingsUiIntent) -> Unit,
) {
    val spacing = LocalSpacing.current
    val snackbarHostState = remember { SnackbarHostState() }
    val msgMemberAdded = stringResource(R.string.settings_feedback_member_added)
    val msgError = stringResource(R.string.settings_feedback_error)
    LaunchedEffect(events) {
        events.collect { event ->
            val msg = when (event) {
                SettingsUiEvent.MemberAdded -> msgMemberAdded
                is SettingsUiEvent.Error -> event.message.ifBlank { msgError }
                else -> return@collect
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    ChoreScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.settings_manage_members_title),
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = detailContentPadding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.household_members),
                    subtitle = stringResource(R.string.household_member_count, uiState.members.size),
                )
            }
            item {
                SectionCard(title = stringResource(R.string.household_members)) {
                    if (uiState.members.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.settings_members_empty_title),
                            message = stringResource(R.string.settings_members_empty_message),
                        )
                    } else {
                        uiState.members.forEach { member ->
                            Text(
                                text = stringResource(
                                    R.string.household_member_line,
                                    member.displayName,
                                    member.role.name.lowercase(),
                                ),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = uiState.memberInput,
                        onValueChange = { onIntent(SettingsUiIntent.MemberInputChanged(it)) },
                        label = { Text(text = stringResource(R.string.household_new_member)) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            autoCorrectEnabled = true,
                        ),
                        enabled = uiState.isOwner,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.household_add_member),
                        onClick = { onIntent(SettingsUiIntent.AddMember) },
                        enabled = uiState.isOwner,
                    )
                }
            }
        }
    }
}
