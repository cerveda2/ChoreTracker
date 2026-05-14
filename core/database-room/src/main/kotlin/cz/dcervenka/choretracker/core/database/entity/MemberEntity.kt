package cz.dcervenka.choretracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val userId: String?,
    val displayName: String,
    val role: String,
    val isCurrentUser: Boolean,
    val email: String? = null,
)
