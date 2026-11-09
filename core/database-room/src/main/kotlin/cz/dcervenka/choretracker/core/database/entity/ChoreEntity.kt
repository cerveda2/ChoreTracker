package cz.dcervenka.choretracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import cz.dcervenka.choretracker.core.database.converter.StringListConverters
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
    val category: String = "OTHER",
    val createdByUserId: String? = null,
    @ColumnInfo(defaultValue = "''")
    @field:TypeConverters(StringListConverters::class)
    val rotationMemberIds: List<String> = emptyList(),
    val rotationStartedAt: Instant? = null,
)
