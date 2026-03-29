package cz.dcervenka.choretracker.core.model.sync

import kotlin.time.Instant

data class SyncState(
    val householdId: String,
    val lastSyncedAt: Instant?,
    val pendingOperations: Int,
)
