package cz.dcervenka.choretracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(tableName = "completions")
data class CompletionEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val choreId: String,
    val createdAt: Instant,
    val createdByUserId: String,
    val note: String?,
)
