package cz.dcervenka.choretracker.core.model.household

import kotlin.time.Instant

data class Invite(
    val id: String,
    val householdId: String,
    val code: String,
    val createdAt: Instant,
    val consumedAt: Instant? = null,
    val targetMemberId: String? = null,
)
