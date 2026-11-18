package cz.dcervenka.choretracker.core.updates.viewmodel

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.android.play.core.ktx.requestCompleteUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val LAST_OFFERED_VERSION = "lastOfferedUpdateVersion"

@HiltViewModel
class InAppUpdateViewModel @Inject constructor(
    private val appUpdateManager: AppUpdateManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mutableAvailableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate = mutableAvailableUpdate.asStateFlow()

    private val mutableUpdateDownloaded = MutableStateFlow(false)
    val isUpdateDownloaded = mutableUpdateDownloaded.asStateFlow()

    private var checkJob: Job? = null
    private var isCompletingUpdate = false

    private val installListener = InstallStateUpdatedListener { state ->
        mutableUpdateDownloaded.value = state.installStatus() == InstallStatus.DOWNLOADED
        mutableAvailableUpdate.value = null
    }

    init {
        appUpdateManager.registerListener(installListener)
    }

    fun checkForUpdate() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            runCatching { appUpdateManager.requestAppUpdateInfo() }
                .onSuccess(::applyUpdateInfo)
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Timber.d(error, "In-app update check unavailable")
                }
        }
    }

    private fun applyUpdateInfo(info: AppUpdateInfo) {
        when (info.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                mutableUpdateDownloaded.value = true
                mutableAvailableUpdate.value = null
            }
            InstallStatus.PENDING, InstallStatus.DOWNLOADING, InstallStatus.INSTALLING -> {
                mutableAvailableUpdate.value = null
            }
            else -> mutableAvailableUpdate.value = info.takeIf {
                !mutableUpdateDownloaded.value &&
                    it.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    it.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                    it.availableVersionCode() != savedStateHandle.get<Int>(LAST_OFFERED_VERSION)
            }
        }
    }

    fun startUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val info = mutableAvailableUpdate.value ?: return
        mutableAvailableUpdate.value = null
        savedStateHandle[LAST_OFFERED_VERSION] = info.availableVersionCode()
        runCatching {
            appUpdateManager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
            )
        }.onFailure { error ->
            Timber.w(error, "Could not start in-app update")
        }
    }

    suspend fun completeUpdate(): Boolean {
        if (!mutableUpdateDownloaded.value || isCompletingUpdate) return false
        isCompletingUpdate = true
        return try {
            runCatching { appUpdateManager.requestCompleteUpdate() }
                .fold(
                    onSuccess = {
                        mutableUpdateDownloaded.value = false
                        true
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        Timber.w(error, "Could not complete in-app update")
                        false
                    },
                )
        } finally {
            isCompletingUpdate = false
        }
    }

    override fun onCleared() {
        appUpdateManager.unregisterListener(installListener)
    }
}
