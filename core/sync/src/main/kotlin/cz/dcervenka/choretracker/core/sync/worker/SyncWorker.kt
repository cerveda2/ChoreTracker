package cz.dcervenka.choretracker.core.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.time.Clock

// About four hours of WorkManager's doubling 30s backoff, so a change the rules keep rejecting
// doesn't retry forever. The next change to the queue, or the next app start, asks again.
private const val MAX_RUNS = 10

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val startedAt = clock.now()
        val synced = syncRepository.syncPendingOperations() is AppResult.Success
        return when {
            // A change queued during this run got no work of its own (KEEP left this one running),
            // so retry until its own push has drained it.
            synced && pendingSyncOperationDao.getAll().none { it.createdAt >= startedAt } -> Result.success()
            runAttemptCount + 1 < MAX_RUNS -> Result.retry()
            else -> Result.failure()
        }
    }
}
