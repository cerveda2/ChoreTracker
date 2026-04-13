package cz.dcervenka.choretracker.core.model.sync

import kotlin.time.Instant

data class SyncState(
    val householdId: String,
    val lastSyncedAt: Instant?,
    val lastSyncAttemptAt: Instant?,
    val pendingOperations: Int,
    val lastErrorMessage: String?,
)
