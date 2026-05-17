package cz.dcervenka.choretracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(tableName = "invites")
data class InviteEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val code: String,
    val createdAt: Instant,
    val consumedAt: Instant?,
    val targetMemberId: String? = null,
    val consumedByMemberId: String? = null,
)
