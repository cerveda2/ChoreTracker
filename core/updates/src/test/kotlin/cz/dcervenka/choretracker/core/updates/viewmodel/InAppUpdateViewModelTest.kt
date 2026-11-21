package cz.dcervenka.choretracker.core.updates.viewmodel

import android.content.Context
import android.os.Looper
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InAppUpdateViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private lateinit var manager: FakeAppUpdateManager
    private val viewModelStore = ViewModelStore()
    private var nextViewModelId = 0

    private val launcher = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) = Unit
    }.register("update", ActivityResultContracts.StartIntentSenderForResult()) { }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        manager = FakeAppUpdateManager(context)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    @Test
    fun `no available update leaves the app alone`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.checkForUpdate()

        assertThat(viewModel.availableUpdate.value).isNull()
        assertThat(viewModel.isUpdateDownloaded.value).isFalse()
        assertThat(manager.isConfirmationDialogVisible).isFalse()
    }

    @Test
    fun `available flexible update opens Play consent`() = runTest(coroutineRule.dispatcher) {
        manager.setUpdateAvailable(42)
        val viewModel = createViewModel()

        viewModel.checkForUpdate()
        viewModel.startUpdate(launcher)

        assertThat(manager.isConfirmationDialogVisible).isTrue()
        assertThat(manager.isImmediateFlowVisible).isFalse()
        assertThat(viewModel.availableUpdate.value).isNull()
    }

    @Test
    fun `an immediate-only update is not forced on the user`() = runTest(coroutineRule.dispatcher) {
        manager.setUpdateAvailable(42, AppUpdateType.IMMEDIATE)
        val viewModel = createViewModel()

        viewModel.checkForUpdate()
        viewModel.startUpdate(launcher)

        assertThat(viewModel.availableUpdate.value).isNull()
        assertThat(manager.isConfirmationDialogVisible).isFalse()
        assertThat(manager.isImmediateFlowVisible).isFalse()
    }

    @Test
    fun `declined update stays dismissed after recreation`() = runTest(coroutineRule.dispatcher) {
        manager.setUpdateAvailable(42)
        val savedStateHandle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle)
        viewModel.checkForUpdate()
        viewModel.startUpdate(launcher)
        manager.userRejectsUpdate()

        viewModel.checkForUpdate()
        assertThat(viewModel.availableUpdate.value).isNull()

        viewModelStore.clear()
        val recreated = createViewModel(savedStateHandle)
        recreated.checkForUpdate()

        assertThat(recreated.availableUpdate.value).isNull()
        assertThat(manager.isConfirmationDialogVisible).isFalse()
    }

    @Test
    fun `a newer version can be offered after declining an earlier version`() = runTest(coroutineRule.dispatcher) {
        manager.setUpdateAvailable(42)
        val viewModel = createViewModel()
        viewModel.checkForUpdate()
        viewModel.startUpdate(launcher)
        manager.userRejectsUpdate()

        manager.setUpdateAvailable(43)
        viewModel.checkForUpdate()

        assertThat(viewModel.availableUpdate.value?.availableVersionCode()).isEqualTo(43)
    }

    @Test
    fun `fresh view model does not offer a running download`() = runTest(coroutineRule.dispatcher) {
        startDownload()

        val recreated = createViewModel()
        recreated.checkForUpdate()

        assertThat(recreated.availableUpdate.value).isNull()
        assertThat(recreated.isUpdateDownloaded.value).isFalse()
    }

    @Test
    fun `a completed download waits for an explicit restart`() = runTest(coroutineRule.dispatcher) {
        val viewModel = startDownload()

        manager.downloadCompletes()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.isUpdateDownloaded.value).isTrue()
        assertThat(manager.isInstallSplashScreenVisible).isFalse()

        assertThat(viewModel.completeUpdate()).isTrue()
        assertThat(manager.isInstallSplashScreenVisible).isTrue()
    }

    @Test
    fun `resume discovers an already downloaded update`() = runTest(coroutineRule.dispatcher) {
        startDownload()
        viewModelStore.clear()
        manager.downloadCompletes()
        shadowOf(Looper.getMainLooper()).idle()

        val recreated = createViewModel()
        recreated.checkForUpdate()

        assertThat(recreated.isUpdateDownloaded.value).isTrue()
        assertThat(recreated.availableUpdate.value).isNull()
        assertThat(manager.isInstallSplashScreenVisible).isFalse()
    }

    @Test
    fun `restart is ignored until a download is ready`() = runTest(coroutineRule.dispatcher) {
        val viewModel = startDownload()

        assertThat(viewModel.completeUpdate()).isFalse()
        assertThat(manager.isInstallSplashScreenVisible).isFalse()
    }

    @Test
    fun `a failed install can be retried without losing the downloaded prompt`() = runTest(coroutineRule.dispatcher) {
        val viewModel = startDownload()
        manager.downloadCompletes()
        shadowOf(Looper.getMainLooper()).idle()
        manager.setInstallErrorCode(InstallErrorCode.ERROR_INTERNAL_ERROR)

        assertThat(viewModel.completeUpdate()).isFalse()
        assertThat(viewModel.isUpdateDownloaded.value).isTrue()

        manager.setInstallErrorCode(InstallErrorCode.NO_ERROR)
        assertThat(viewModel.completeUpdate()).isTrue()
        assertThat(manager.isInstallSplashScreenVisible).isTrue()
    }

    @Test
    fun `a cancelled download neither restarts nor reopens consent`() = runTest(coroutineRule.dispatcher) {
        val viewModel = startDownload()

        manager.userCancelsDownload()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.checkForUpdate()

        assertThat(viewModel.isUpdateDownloaded.value).isFalse()
        assertThat(viewModel.availableUpdate.value).isNull()
        assertThat(manager.isInstallSplashScreenVisible).isFalse()
    }

    @Test
    fun `a failed download leaves the app usable`() = runTest(coroutineRule.dispatcher) {
        val viewModel = startDownload()

        manager.downloadFails()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.checkForUpdate()

        assertThat(viewModel.isUpdateDownloaded.value).isFalse()
        assertThat(viewModel.availableUpdate.value).isNull()
        assertThat(manager.isInstallSplashScreenVisible).isFalse()
    }

    @Test
    fun `an unavailable Play service is harmless and a later check can recover`() = runTest(coroutineRule.dispatcher) {
        manager.setUpdateAvailable(42)
        manager.setInstallErrorCode(InstallErrorCode.ERROR_API_NOT_AVAILABLE)
        val viewModel = createViewModel()

        viewModel.checkForUpdate()
        assertThat(viewModel.availableUpdate.value).isNull()

        manager.setInstallErrorCode(InstallErrorCode.NO_ERROR)
        viewModel.checkForUpdate()
        assertThat(viewModel.availableUpdate.value?.availableVersionCode()).isEqualTo(42)
    }

    @Test
    fun `clearing the view model removes its download listener`() = runTest(coroutineRule.dispatcher) {
        val viewModel = startDownload()
        viewModelStore.clear()

        manager.downloadCompletes()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.isUpdateDownloaded.value).isFalse()
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): InAppUpdateViewModel =
        InAppUpdateViewModel(manager, savedStateHandle).also {
            viewModelStore.put("updates-${nextViewModelId++}", it)
        }

    private fun startDownload(): InAppUpdateViewModel {
        manager.setUpdateAvailable(42)
        val viewModel = createViewModel()
        viewModel.checkForUpdate()
        viewModel.startUpdate(launcher)
        manager.userAcceptsUpdate()
        manager.downloadStarts()
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }
}
