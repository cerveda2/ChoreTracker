package cz.dcervenka.choretracker.core.sync.scheduler

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.sync.di.SyncScope
import cz.dcervenka.choretracker.core.sync.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

private const val UNIQUE_WORK_NAME = "pending_sync_work"

// Longer than the 20s push timeout, so the push each write starts drains the queue (and the
// work is cancelled) before the worker runs, unless that push failed.
private const val INITIAL_DELAY_SECONDS = 30L

/**
 * Keeps a network-constrained [SyncWorker] scheduled while local changes wait to be uploaded, so
 * they go out once the device is back online, even if the app has been closed since.
 */
@Singleton
class PendingSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val authRepository: AuthRepository,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
    @SyncScope private val scope: CoroutineScope,
) {
    init {
        authRepository.authState
            // Leaves work alone until the session is restored: in a process WorkManager started
            // to run the worker, cancelling here would stop that very run.
            .filterNot { it is AuthState.Initializing }
            .flatMapLatest { authState ->
                val user = (authState as? AuthState.Authenticated)?.user
                if (user == null || user.isPreview) {
                    flowOf(false)
                } else {
                    pendingSyncOperationDao.observePendingCount().map { it > 0 }
                }
            }
            .distinctUntilChanged()
            .onEach { hasPendingOperations ->
                if (hasPendingOperations) enqueue() else workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            }
            .retry { error ->
                Timber.e(error, "PendingSyncScheduler: pending operations subscription failed, retrying")
                delay(5.seconds)
                true
            }
            .launchIn(scope)
    }

    // KEEP: work already waiting for the network, or running, stays as it is.
    private fun enqueue() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
