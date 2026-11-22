package cz.dcervenka.choretracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(tableName = "personal_completions")
data class PersonalCompletionEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val choreId: String,
    val createdAt: Instant,
    val note: String?,
)
