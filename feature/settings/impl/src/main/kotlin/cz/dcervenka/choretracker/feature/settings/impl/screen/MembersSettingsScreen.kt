package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
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
    val msgMemberDeleted = stringResource(R.string.settings_feedback_member_deleted)
    val msgError = stringResource(R.string.settings_feedback_error)
    var memberToDelete by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(events) {
        events.collect { event ->
            val msg = when (event) {
                SettingsUiEvent.MemberAdded -> msgMemberAdded
                SettingsUiEvent.MemberDeleted -> msgMemberDeleted
                is SettingsUiEvent.Error -> event.message.ifBlank { msgError }
                else -> return@collect
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (memberToDelete != null) {
        DeleteMemberDialog(
            onConfirm = {
                memberToDelete?.let { onIntent(SettingsUiIntent.DeleteMember(it)) }
                memberToDelete = null
            },
            onDismiss = { memberToDelete = null },
        )
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
                            MemberRow(
                                member = member,
                                isOwner = uiState.isOwner,
                                onDeleteClick = { memberToDelete = member.id },
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

@Composable
private fun MemberRow(
    member: HouseholdMember,
    isOwner: Boolean,
    onDeleteClick: () -> Unit,
) {
    val canDelete = isOwner && !member.isCurrentUser && member.role != HouseholdRole.OWNER
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (member.isCurrentUser) {
                    stringResource(
                        R.string.household_member_line,
                        member.displayName,
                        member.role.name.lowercase(),
                    ) + " • " + stringResource(R.string.household_member_you)
                } else {
                    stringResource(
                        R.string.household_member_line,
                        member.displayName,
                        member.role.name.lowercase(),
                    )
                },
                color = if (member.isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            member.email?.let { email ->
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (canDelete) {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.PersonRemove,
                    contentDescription = stringResource(R.string.household_delete_member),
                )
            }
        }
    }
}

@Composable
private fun DeleteMemberDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.household_delete_member_title)) },
        text = { Text(stringResource(R.string.household_delete_member_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.household_delete_member))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
