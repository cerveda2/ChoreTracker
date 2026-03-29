package cz.dcervenka.choretracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(tableName = "sync_states")
data class SyncStateEntity(
    @PrimaryKey val householdId: String,
    val lastSyncedAt: Instant?,
    val pendingOperations: Int,
)
