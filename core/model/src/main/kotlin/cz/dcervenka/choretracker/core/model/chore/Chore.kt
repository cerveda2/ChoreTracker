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
)
