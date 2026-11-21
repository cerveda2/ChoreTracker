package cz.dcervenka.choretracker.core.updates.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import cz.dcervenka.choretracker.core.updates.R
import cz.dcervenka.choretracker.core.updates.viewmodel.InAppUpdateViewModel
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun InAppUpdatePrompt(
    snackbarHostState: SnackbarHostState,
    viewModel: InAppUpdateViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    val isDownloaded by viewModel.isUpdateDownloaded.collectAsStateWithLifecycle()
    val downloadedMessage = stringResource(R.string.update_downloaded)
    val restartLabel = stringResource(R.string.update_restart)
    val failedMessage = stringResource(R.string.update_install_failed)

    LaunchedEffect(lifecycleOwner, viewModel, launcher) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.checkForUpdate()
            viewModel.availableUpdate.filterNotNull().collect {
                viewModel.startUpdate(launcher)
            }
        }
    }

    LaunchedEffect(isDownloaded, lifecycleOwner, snackbarHostState, downloadedMessage, restartLabel, failedMessage) {
        if (!isDownloaded) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var message = downloadedMessage
            while (viewModel.isUpdateDownloaded.value && !viewModel.isRestartPromptDismissed) {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = restartLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite,
                )
                if (result != SnackbarResult.ActionPerformed) viewModel.dismissRestartPrompt()
                if (result != SnackbarResult.ActionPerformed || viewModel.completeUpdate()) break
                message = failedMessage
            }
        }
    }
}
