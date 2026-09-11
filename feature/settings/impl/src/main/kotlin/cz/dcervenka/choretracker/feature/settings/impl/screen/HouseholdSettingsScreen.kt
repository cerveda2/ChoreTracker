package cz.dcervenka.choretracker.feature.settings.impl.screen

import android.content.ClipData
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.SectionHeader
import cz.dcervenka.choretracker.core.model.household.Invite
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiEvent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrColors
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
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
                SettingsUiEvent.OwnershipTransferred -> {
                    onBack()
                    return@collect
                }
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
    val transferSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showTransferPicker by rememberSaveable { mutableStateOf(false) }
    var transferTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val transferTarget = uiState.members.firstOrNull { it.id == transferTargetId }

    if (showTransferPicker) {
        ModalBottomSheet(
            onDismissRequest = { showTransferPicker = false },
            sheetState = transferSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large)
                    .padding(bottom = spacing.large),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Text(
                    text = stringResource(R.string.settings_transfer_ownership_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = spacing.small),
                )
                uiState.eligibleTransferTargets.forEach { member ->
                    TextButton(
                        onClick = {
                            transferTargetId = member.id
                            showTransferPicker = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = member.displayName, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (transferTarget != null) {
        AlertDialog(
            onDismissRequest = { transferTargetId = null },
            title = { Text(stringResource(R.string.settings_transfer_ownership_title, transferTarget.displayName)) },
            text = { Text(stringResource(R.string.settings_transfer_ownership_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onIntent(SettingsUiIntent.TransferOwnership(transferTarget.id))
                    transferTargetId = null
                }) {
                    Text(stringResource(R.string.settings_transfer_ownership_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { transferTargetId = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

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
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = detailContentPadding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            if (uiState.household != null) {
                item {
                    ListGroup {
                        Column(
                            modifier = Modifier.padding(spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(spacing.medium),
                        ) {
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
                }
                item {
                    val hasActiveInvite = uiState.invites.isNotEmpty() &&
                        uiState.invites.any { it.code == uiState.household.inviteCode && it.consumedAt == null }
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        SectionHeader(title = stringResource(R.string.household_invite_section))
                        ListGroup {
                            Column(
                                modifier = Modifier.padding(spacing.medium),
                                verticalArrangement = Arrangement.spacedBy(spacing.medium),
                            ) {
                                if (hasActiveInvite) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.settings_invite_code,
                                                uiState.household.inviteCode,
                                            ),
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
                                            shareInviteCode(context, shareMessage.format(uiState.household.inviteCode))
                                        }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Share,
                                                contentDescription = stringResource(R.string.settings_invite_share),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            painter = rememberQrCodePainter(
                                                data = uiState.household.inviteCode,
                                                colors = QrColors(
                                                    dark = QrBrush.solid(MaterialTheme.colorScheme.onSurface),
                                                ),
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(180.dp),
                                        )
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
                                    )
                                    val labelOpen = stringResource(R.string.settings_invite_label_open)
                                    val labelForMember = stringResource(R.string.settings_invite_label_for_member)
                                    uiState.invites.forEach { invite ->
                                        val label = when {
                                            invite.consumedByMemberId != null -> {
                                                val memberName =
                                                    uiState.members.find { it.id == invite.consumedByMemberId }?.displayName
                                                labelForMember.format(memberName ?: "?")
                                            }
                                            invite.targetMemberId != null -> {
                                                val memberName =
                                                    uiState.members.find { it.id == invite.targetMemberId }?.displayName
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
                    }
                }
                if (uiState.isOwner) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                            SectionHeader(title = stringResource(R.string.settings_membership_section))
                            ListGroup {
                                TextButton(
                                    onClick = { showTransferPicker = true },
                                    enabled = uiState.eligibleTransferTargets.isNotEmpty(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(spacing.small),
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_transfer_ownership),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            if (uiState.eligibleTransferTargets.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.settings_transfer_ownership_none),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = spacing.small),
                                )
                            }
                        }
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
    var expanded by remember { mutableStateOf(false) }
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
                    contentDescription = stringResource(R.string.settings_invite_copy),
                    modifier = Modifier.size(16.dp),
                )
            }
            if (isPending) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.settings_invite_collapse_qr else R.string.settings_invite_expand_qr,
                        ),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = invite.code,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isPending && expanded) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = rememberQrCodePainter(
                        data = invite.code,
                        colors = QrColors(dark = QrBrush.solid(MaterialTheme.colorScheme.onSurface)),
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(160.dp),
                )
            }
        }
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
