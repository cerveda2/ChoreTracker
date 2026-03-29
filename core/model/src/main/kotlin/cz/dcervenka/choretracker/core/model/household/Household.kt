package cz.dcervenka.choretracker.core.model.household

import kotlin.time.Instant

data class Household(
    val id: String,
    val name: String,
    val ownerUserId: String,
    val inviteCode: String,
    val createdAt: Instant,
)
