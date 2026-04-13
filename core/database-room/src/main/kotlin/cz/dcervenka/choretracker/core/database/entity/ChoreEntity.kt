package cz.dcervenka.choretracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(tableName = "chores")
data class ChoreEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val deletedAt: Instant?,
    val frequencyDays: Int? = null,
)
