package cz.dcervenka.choretracker.core.model.chore

import kotlin.time.Instant

data class Chore(
    val id: String,
    val householdId: String,
    val name: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val deletedAt: Instant? = null,
    val frequencyDays: Int? = null,
    val category: ChoreCategory = ChoreCategory.OTHER,
    val createdByUserId: String? = null,
    // Members (HouseholdMember.id) who take turns; empty when the chore doesn't rotate.
    val rotationMemberIds: List<String> = emptyList(),
    // Only completions from this moment on count toward whose turn it is.
    val rotationStartedAt: Instant? = null,
)
