package cz.dcervenka.choretracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import cz.dcervenka.choretracker.core.database.converter.WeekdaySetConverters
import kotlinx.datetime.DayOfWeek
import kotlin.time.Instant

// A chore only its owner sees, kept apart from the household's chores so no household query can pick it up.
@Entity(tableName = "personal_chores")
data class PersonalChoreEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val name: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val deletedAt: Instant?,
    val frequencyDays: Int? = null,
    val category: String = "OTHER",
    @ColumnInfo(defaultValue = "0")
    @field:TypeConverters(WeekdaySetConverters::class)
    val scheduleWeekdays: Set<DayOfWeek> = emptySet(),
    val scheduleMonthDay: Int? = null,
)
