package cz.dcervenka.choretracker.core.model.chore

import kotlin.time.Instant

data class ChoreCompletion(
    val id: String,
    val householdId: String,
    val choreId: String,
    val createdAt: Instant,
    val createdByUserId: String,
    val note: String?,
    val participantMemberIds: List<String>,
)
