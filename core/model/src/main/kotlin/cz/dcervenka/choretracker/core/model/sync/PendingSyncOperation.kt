package cz.dcervenka.choretracker.core.model.sync

import kotlin.time.Instant

data class PendingSyncOperation(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payload: String,
    val createdAt: Instant,
)
