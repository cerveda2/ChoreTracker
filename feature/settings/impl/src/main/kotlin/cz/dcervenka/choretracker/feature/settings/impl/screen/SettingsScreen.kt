package cz.dcervenka.choretracker.feature.settings.impl.screen

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalMemberPalette
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreListRow
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.IconCircle
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.MemberAvatar
import cz.dcervenka.choretracker.core.design.components.SectionHeader
import cz.dcervenka.choretracker.core.design.components.TopLevelBottomBarSpacer
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.settings.ThemeMode
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState

private const val MAX_AVATAR_STACK = 3

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onOpenHousehold: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenTheme: () -> Unit,
    onSignOut: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    var showSignOutConfirm by rememberSaveable { mutableStateOf(false) }

    val currentMemberIndex = uiState.members.indexOfFirst { it.isCurrentUser }
    val profileTitle = uiState.userLabel ?: when {
        uiState.requiresConfiguration -> stringResource(R.string.settings_firebase_required)
        uiState.isSignedOut -> stringResource(R.string.settings_signed_out)
        else -> stringResource(R.string.settings_loading)
    }
    val currentRole = uiState.members.getOrNull(currentMemberIndex)?.role
    val roleLabel = currentRole?.let {
        stringResource(if (it == HouseholdRole.OWNER) R.string.household_role_owner else R.string.household_role_member)
    }
    val profileSubtitle = when {
        uiState.userEmail != null && roleLabel != null ->
            stringResource(R.string.settings_profile_email_role, uiState.userEmail, roleLabel)
        uiState.userEmail != null -> uiState.userEmail
        uiState.requiresConfiguration -> stringResource(R.string.settings_not_configured_summary)
        else -> stringResource(R.string.settings_no_household_summary)
    }
    val themeSummary = stringResource(uiState.themeSettings.mode.summaryRes())
    val reminderSummary = if (uiState.reminderSettings.enabled) {
        stringResource(
            R.string.settings_notifications_summary_on,
            "%02d:%02d".format(uiState.reminderSettings.hour, uiState.reminderSettings.minute),
        )
    } else {
        stringResource(R.string.settings_notifications_summary_off)
    }

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(title = stringResource(R.string.settings_title))
        },
        bottomBar = { TopLevelBottomBarSpacer() },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.large,
                top = innerPadding.calculateTopPadding() + spacing.medium,
                end = spacing.large,
                bottom = innerPadding.calculateBottomPadding() + spacing.large,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            item(key = "profile") {
                ListGroup {
                    ChoreListRow(
                        leading = {
                            MemberAvatar(
                                initial = profileTitle.take(1),
                                color = if (currentMemberIndex >= 0) {
                                    LocalMemberPalette.current.color(currentMemberIndex)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                size = 56.dp,
                            )
                        },
                        title = profileTitle,
                        subtitle = profileSubtitle,
                        onClick = onOpenAccount,
                    )
                }
            }
            item(key = "household") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    SectionHeader(title = stringResource(R.string.household_title))
                    ListGroup {
                        ChoreListRow(
                            leading = { IconCircle(icon = Icons.Outlined.Home) },
                            title = uiState.household?.name ?: stringResource(R.string.household_no_household),
                            onClick = onOpenHousehold,
                        )
                        HorizontalDivider()
                        ChoreListRow(
                            leading = { OverlappingMemberAvatars(members = uiState.members) },
                            title = stringResource(R.string.household_members),
                            subtitle = pluralStringResource(
                                R.plurals.settings_members_description,
                                uiState.members.size,
                                uiState.members.size,
                            ),
                            onClick = onOpenMembers,
                        )
                        uiState.household?.let { household ->
                            HorizontalDivider()
                            val shareMessage = stringResource(R.string.settings_invite_share_message)
                            ChoreListRow(
                                leading = { IconCircle(icon = Icons.Outlined.PersonAdd) },
                                title = stringResource(R.string.settings_invite_partner_title),
                                trailing = {
                                    FilledTonalIconButton(
                                        onClick = {
                                            shareInviteCode(
                                                context,
                                                shareMessage.format(household.inviteCode),
                                            )
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Share,
                                            contentDescription = stringResource(R.string.settings_invite_share),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item(key = "preferences") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    SectionHeader(title = stringResource(R.string.settings_preferences_group))
                    ListGroup {
                        ChoreListRow(
                            leading = { IconCircle(icon = Icons.Outlined.Palette) },
                            title = stringResource(R.string.settings_theme_title),
                            subtitle = themeSummary,
                            onClick = onOpenTheme,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            HorizontalDivider()
                            ChoreListRow(
                                leading = { IconCircle(icon = Icons.Outlined.Translate) },
                                title = stringResource(R.string.settings_language_title),
                                subtitle = stringResource(R.string.settings_language_description),
                                onClick = onOpenLanguage,
                            )
                        }
                        HorizontalDivider()
                        ChoreListRow(
                            leading = { IconCircle(icon = Icons.Outlined.Notifications) },
                            title = stringResource(R.string.settings_notifications_title),
                            subtitle = reminderSummary,
                            onClick = onOpenNotifications,
                        )
                        HorizontalDivider()
                        ChoreListRow(
                            leading = { IconCircle(icon = Icons.Outlined.Info) },
                            title = stringResource(R.string.settings_about_title),
                            subtitle = stringResource(R.string.settings_about_version, appVersionName(context)),
                        )
                    }
                }
            }
            item(key = "sign_out") {
                TextButton(onClick = { showSignOutConfirm = true }) {
                    Text(stringResource(R.string.settings_sign_out))
                }
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(stringResource(R.string.settings_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_supporting)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    },
                ) {
                    Text(stringResource(R.string.settings_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun OverlappingMemberAvatars(members: List<HouseholdMember>, modifier: Modifier = Modifier) {
    val palette = LocalMemberPalette.current
    val shown = members.take(MAX_AVATAR_STACK)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
        shown.forEachIndexed { index, member ->
            MemberAvatar(
                initial = member.displayName.take(1),
                color = palette.color(index),
                size = 28.dp,
                modifier = Modifier
                    .zIndex((shown.size - index).toFloat())
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
            )
        }
    }
}

private fun ThemeMode.summaryRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun appVersionName(context: Context): String {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    return packageInfo.versionName.orEmpty()
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun SettingsScreenPreview() {
    ChoreTrackerTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                userLabel = "Dana",
                userEmail = "dana@example.com",
                household = PreviewData.household,
                members = PreviewData.members,
            ),
            onOpenHousehold = {},
            onOpenMembers = {},
            onOpenAccount = {},
            onOpenLanguage = {},
            onOpenNotifications = {},
            onOpenTheme = {},
            onSignOut = {},
        )
    }
}
