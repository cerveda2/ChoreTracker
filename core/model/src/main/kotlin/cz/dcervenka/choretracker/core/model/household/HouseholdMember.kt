package cz.dcervenka.choretracker.core.model.household

import kotlin.time.Instant

data class HouseholdMember(
    val id: String,
    val householdId: String,
    val userId: String?,
    val displayName: String,
    val role: HouseholdRole,
    val isCurrentUser: Boolean = false,
    val email: String? = null,
    val joinedViaInviteId: String? = null,
    // Soft-removal stamp, mirroring Chore.deletedAt. A removed member's row is kept (so history
    // keeps resolving their name) but they drop out of the current-member lists and lose
    // household access - Firestore's `active` field is derived from this (active = removedAt == null).
    val removedAt: Instant? = null,
)
