package cz.dcervenka.choretracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(tableName = "chore_requests")
data class ChoreRequestEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val batchId: String,
    val choreId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val toUserId: String,
    val createdByUserId: String,
    val note: String?,
    val createdAt: Instant,
    val state: String,
    val resolvedAt: Instant?,
)
