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
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalMemberPalette
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreListRow
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.MemberAvatar
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.SectionHeader
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiEvent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrColors
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersSettingsScreen(
    uiState: SettingsUiState,
    events: Flow<SettingsUiEvent>,
    onBack: () -> Unit,
    onIntent: (SettingsUiIntent) -> Unit,
) {
    val spacing = LocalSpacing.current
    val palette = LocalMemberPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    val msgMemberAdded = stringResource(R.string.settings_feedback_member_added)
    val msgMemberRemoved = stringResource(R.string.settings_feedback_member_deleted)
    val msgError = stringResource(R.string.settings_feedback_error)
    var memberToRemove by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetMemberId by rememberSaveable { mutableStateOf<String?>(null) }
    var memberInviteCode by rememberSaveable { mutableStateOf<String?>(null) }
    var memberInviteName by rememberSaveable { mutableStateOf("") }
    val inviteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val memberSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareMessage = stringResource(R.string.settings_invite_share_message)

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                SettingsUiEvent.MemberAdded -> snackbarHostState.showSnackbar(msgMemberAdded)
                SettingsUiEvent.MemberRemoved -> snackbarHostState.showSnackbar(msgMemberRemoved)
                is SettingsUiEvent.Error -> snackbarHostState.showSnackbar(event.message.ifBlank { msgError })
                is SettingsUiEvent.MemberInviteGenerated -> memberInviteCode = event.code
                else -> Unit
            }
        }
    }

    val sheetMember = uiState.members.firstOrNull { it.id == sheetMemberId }
    if (sheetMember != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetMemberId = null },
            sheetState = memberSheetState,
        ) {
            MemberSheetContent(
                member = sheetMember,
                color = palette.color(uiState.members.indexOf(sheetMember).coerceAtLeast(0)),
                canInvite = uiState.isOwner && sheetMember.userId == null,
                canRemove = uiState.isOwner &&
                    !sheetMember.isCurrentUser &&
                    sheetMember.role != HouseholdRole.OWNER,
                onInviteClick = {
                    memberInviteName = sheetMember.displayName
                    onIntent(SettingsUiIntent.GenerateMemberInvite(sheetMember.id))
                    sheetMemberId = null
                },
                onRemoveClick = {
                    memberToRemove = sheetMember.id
                    sheetMemberId = null
                },
            )
        }
    }

    memberInviteCode?.let { code ->
        ModalBottomSheet(
            onDismissRequest = { memberInviteCode = null },
            sheetState = inviteSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large, vertical = spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                Text(
                    text = stringResource(R.string.settings_member_invite_title, memberInviteName),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_invite_code, code),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = rememberQrCodePainter(
                            data = code,
                            colors = QrColors(dark = QrBrush.solid(MaterialTheme.colorScheme.onSurface)),
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(180.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    TextButton(onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", code)))
                        }
                    }) {
                        Text(stringResource(R.string.settings_invite_copy))
                    }
                    TextButton(onClick = { shareInviteCode(context, shareMessage.format(code)) }) {
                        Text(stringResource(R.string.settings_invite_share))
                    }
                }
            }
        }
    }

    if (memberToRemove != null) {
        RemoveMemberDialog(
            onConfirm = {
                memberToRemove?.let { onIntent(SettingsUiIntent.RemoveMember(it)) }
                memberToRemove = null
            },
            onDismiss = { memberToRemove = null },
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
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = detailContentPadding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    SectionHeader(title = stringResource(R.string.household_members))
                    ListGroup {
                        if (uiState.members.isEmpty()) {
                            EmptyState(
                                title = stringResource(R.string.settings_members_empty_title),
                                message = stringResource(R.string.settings_members_empty_message),
                                modifier = Modifier.padding(spacing.medium),
                            )
                        } else {
                            uiState.members.forEachIndexed { index, member ->
                                if (index > 0) HorizontalDivider()
                                ChoreListRow(
                                    leading = {
                                        MemberAvatar(
                                            initial = member.displayName.take(1),
                                            color = palette.color(index),
                                        )
                                    },
                                    title = member.displayName,
                                    subtitle = memberSubtitle(member),
                                    subtitleColor = if (member.isCurrentUser) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    onClick = { sheetMemberId = member.id },
                                )
                            }
                        }
                    }
                }
            }
            item {
                ListGroup {
                    Column(
                        modifier = Modifier.padding(spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.medium),
                    ) {
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
                            enabled = uiState.isOwner && uiState.memberInput.isNotBlank(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun memberSubtitle(member: HouseholdMember): String {
    val role = member.role.localizedName()
    return if (member.isCurrentUser) {
        stringResource(R.string.household_member_line, role, stringResource(R.string.household_member_you))
    } else {
        role
    }
}

@Composable
private fun MemberSheetContent(
    member: HouseholdMember,
    color: Color,
    canInvite: Boolean,
    canRemove: Boolean,
    onInviteClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large)
            .padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            modifier = Modifier.padding(vertical = spacing.small),
        ) {
            MemberAvatar(initial = member.displayName.take(1), color = color, size = 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = member.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = member.email ?: member.role.localizedName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (canInvite) {
            HorizontalDivider()
            TextButton(
                onClick = onInviteClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Outlined.Link, contentDescription = null)
                Text(
                    text = stringResource(R.string.settings_member_invite_action),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = spacing.medium),
                )
            }
        }
        if (canRemove) {
            HorizontalDivider()
            TextButton(
                onClick = onRemoveClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonRemove,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.household_delete_member),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = spacing.medium),
                )
            }
        }
    }
}

@Composable
private fun RemoveMemberDialog(
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

@Composable
private fun HouseholdRole.localizedName(): String = stringResource(
    when (this) {
        HouseholdRole.OWNER -> R.string.household_role_owner
        HouseholdRole.MEMBER -> R.string.household_role_member
    },
)
