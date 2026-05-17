package cz.dcervenka.choretracker.feature.settings.impl.screen

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.model.household.Invite
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiEvent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun HouseholdSettingsScreen(
    uiState: SettingsUiState,
    events: Flow<SettingsUiEvent>,
    onBack: () -> Unit,
    onIntent: (SettingsUiIntent) -> Unit,
) {
    val spacing = LocalSpacing.current
    val snackbarHostState = remember { SnackbarHostState() }
    val msgNameSaved = stringResource(R.string.settings_feedback_name_saved)
    val msgError = stringResource(R.string.settings_feedback_error)
    LaunchedEffect(events) {
        events.collect { event ->
            val msg = when (event) {
                SettingsUiEvent.NameSaved -> msgNameSaved
                is SettingsUiEvent.Error -> event.message.ifBlank { msgError }
                else -> return@collect
            }
            snackbarHostState.showSnackbar(msg)
        }
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val shareMessage = stringResource(R.string.settings_invite_share_message)

    ChoreScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.settings_manage_household_title),
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
                    title = uiState.household?.name ?: stringResource(R.string.household_no_household),
                    subtitle = uiState.household?.let {
                        stringResource(R.string.settings_invite_code, it.inviteCode)
                    },
                )
            }
            if (uiState.household != null) {
                item {
                    SectionCard(title = stringResource(R.string.settings_household_title)) {
                        OutlinedTextField(
                            value = uiState.householdNameInput,
                            onValueChange = { onIntent(SettingsUiIntent.HouseholdNameChanged(it)) },
                            label = { Text(text = stringResource(R.string.settings_household_name)) },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                autoCorrectEnabled = true,
                            ),
                            enabled = uiState.isOwner,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PrimaryButton(
                            text = stringResource(R.string.settings_save_household_name),
                            onClick = { onIntent(SettingsUiIntent.SaveHouseholdName) },
                            enabled = uiState.isOwner,
                        )
                    }
                }
                item {
                    val hasActiveInvite = uiState.invites.isNotEmpty() &&
                        uiState.invites.any { it.code == uiState.household.inviteCode && it.consumedAt == null }
                    SectionCard(title = stringResource(R.string.household_invite_section)) {
                        if (hasActiveInvite) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_invite_code, uiState.household.inviteCode),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText("", uiState.household.inviteCode)),
                                        )
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = stringResource(R.string.settings_invite_copy),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareMessage.format(uiState.household.inviteCode))
                                    }
                                    context.startActivity(Intent.createChooser(intent, null))
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = stringResource(R.string.settings_invite_share),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.settings_invite_none),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (uiState.invites.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                text = stringResource(R.string.settings_invite_history_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = spacing.xSmall),
                            )
                            val labelOpen = stringResource(R.string.settings_invite_label_open)
                            val labelForMember = stringResource(R.string.settings_invite_label_for_member)
                            uiState.invites.forEach { invite ->
                                val label = when {
                                    invite.consumedByMemberId != null -> {
                                        val memberName = uiState.members.find { it.id == invite.consumedByMemberId }?.displayName
                                        labelForMember.format(memberName ?: "?")
                                    }
                                    invite.targetMemberId != null -> {
                                        val memberName = uiState.members.find { it.id == invite.targetMemberId }?.displayName
                                        labelForMember.format(memberName ?: "?")
                                    }
                                    else -> labelOpen
                                }
                                InviteRow(
                                    invite = invite,
                                    label = label,
                                    onCopy = {
                                        scope.launch {
                                            clipboard.setClipEntry(
                                                ClipEntry(ClipData.newPlainText("", invite.code)),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        PrimaryButton(
                            text = stringResource(R.string.household_refresh_invite),
                            onClick = { onIntent(SettingsUiIntent.RefreshInvite) },
                            enabled = uiState.isOwner,
                        )
                    }
                }
            } else {
                item {
                    EmptyState(
                        title = stringResource(R.string.settings_household_empty_title),
                        message = stringResource(R.string.settings_household_empty_message),
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteRow(
    invite: Invite,
    label: String,
    onCopy: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val isPending = invite.consumedAt == null
    val statusText = stringResource(
        if (isPending) R.string.settings_invite_status_pending else R.string.settings_invite_status_joined,
    )
    val dateLabel = DateFormat.getDateInstance(DateFormat.SHORT)
        .format(Date(invite.createdAt.toEpochMilliseconds()))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$dateLabel · $statusText",
                style = MaterialTheme.typography.bodySmall,
                color = if (isPending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
            )
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = invite.code,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HouseholdSettingsScreenPreview() {
    ChoreTrackerTheme {
        HouseholdSettingsScreen(
            uiState = SettingsUiState(
                household = PreviewData.household,
                householdNameInput = PreviewData.household.name,
            ),
            events = emptyFlow(),
            onBack = {},
            onIntent = {},
        )
    }
}
