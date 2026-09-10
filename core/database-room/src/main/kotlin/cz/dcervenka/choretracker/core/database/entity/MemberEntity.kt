package cz.dcervenka.choretracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val userId: String?,
    val displayName: String,
    val role: String,
    val isCurrentUser: Boolean,
    val email: String? = null,
    // Set only when this member row was created/claimed via a join (never for owner-added
    // placeholders). Synced to Firestore so the security rules can verify a self-join is backed
    // by a real, valid invite - see firestore.rules' isSelfJoinWithValidInvite.
    val joinedViaInviteId: String? = null,
    // Soft-removal stamp, mirroring ChoreEntity.deletedAt. The row is kept after removal so chore
    // history keeps resolving the member's name; observeMembers filters these out at the
    // repository layer. Firestore's `active` field is derived from this.
    val removedAt: Instant? = null,
)
