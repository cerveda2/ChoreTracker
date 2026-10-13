package cz.dcervenka.choretracker.core.model.sync

import cz.dcervenka.choretracker.core.model.error.AppError
import kotlin.time.Instant

data class SyncState(
    val householdId: String,
    val lastSyncedAt: Instant?,
    val lastSyncAttemptAt: Instant?,
    val pendingOperations: Int,
    val lastError: AppError?,
)
