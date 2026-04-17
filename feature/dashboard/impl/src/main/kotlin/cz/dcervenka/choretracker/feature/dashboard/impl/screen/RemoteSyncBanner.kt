package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.model.sync.SyncState

@Composable
internal fun RemoteSyncBanner(
    syncState: SyncState,
    onRetrySync: () -> Unit,
) {
    val isError = !syncState.lastErrorMessage.isNullOrBlank()
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(LocalSpacing.current.medium),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.xSmall),
        ) {
            Text(
                text = stringResource(
                    id = if (isError) {
                        R.string.dashboard_sync_failed_title
                    } else {
                        R.string.dashboard_sync_pending_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = syncBannerMessage(syncState),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (syncState.pendingOperations > 0) {
                TextButton(onClick = onRetrySync) {
                    Text(text = stringResource(R.string.common_retry))
                }
            }
        }
    }
}

@Composable
private fun syncBannerMessage(syncState: SyncState): String {
    val errorMessage = syncState.lastErrorMessage.orEmpty()
    return when {
        errorMessage.contains("Missing or insufficient permissions", ignoreCase = true) ->
            stringResource(R.string.dashboard_sync_failed_permissions)
        errorMessage.isNotBlank() ->
            stringResource(R.string.dashboard_sync_failed_generic)
        else -> stringResource(
            R.string.dashboard_sync_pending_message,
            syncState.pendingOperations,
        )
    }
}
