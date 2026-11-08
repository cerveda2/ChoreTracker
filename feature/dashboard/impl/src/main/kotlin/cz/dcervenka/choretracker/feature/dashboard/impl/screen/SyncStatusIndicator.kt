package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.model.sync.SyncState
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.SyncStatus

@Composable
internal fun SyncStatusChip(
    status: SyncStatus,
    onClick: () -> Unit,
) {
    val (icon, label) = when (status) {
        SyncStatus.SYNCED -> Icons.Outlined.CloudDone to R.string.dashboard_sync_ok
        SyncStatus.OFFLINE -> Icons.Outlined.CloudOff to R.string.dashboard_sync_offline
        SyncStatus.NOT_SYNCED -> Icons.Outlined.SyncProblem to R.string.dashboard_sync_issue
    }
    val color = when (status) {
        SyncStatus.SYNCED -> MaterialTheme.colorScheme.primary
        SyncStatus.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
        SyncStatus.NOT_SYNCED -> MaterialTheme.colorScheme.error
    }
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(label))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SyncStatusSheet(
    status: SyncStatus,
    syncState: SyncState?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            val (title, message) = when (status) {
                SyncStatus.SYNCED -> stringResource(R.string.dashboard_sync_status_synced_title) to
                    stringResource(R.string.dashboard_sync_status_synced_message)
                SyncStatus.OFFLINE -> stringResource(R.string.dashboard_sync_status_offline_title) to
                    stringResource(R.string.dashboard_sync_status_offline_message)
                SyncStatus.NOT_SYNCED -> stringResource(R.string.dashboard_sync_failed_title) to
                    (syncState?.let { syncBannerMessage(it) } ?: stringResource(R.string.dashboard_sync_failed_generic))
            }
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status == SyncStatus.NOT_SYNCED) {
                PrimaryButton(
                    text = stringResource(R.string.common_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// Validated, not just connected: a captive portal or a dead Wi-Fi can't reach Firestore either.
@Composable
internal fun ReportNetworkAvailability(onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val currentOnChange by rememberUpdatedState(onChange)
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        fun report(capabilities: NetworkCapabilities?) =
            currentOnChange(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)

        report(connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
                report(networkCapabilities)

            override fun onLost(network: Network) = report(null)
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
}
