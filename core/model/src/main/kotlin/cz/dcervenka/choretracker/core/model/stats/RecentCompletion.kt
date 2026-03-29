package cz.dcervenka.choretracker.core.model.stats

import kotlin.time.Instant

data class RecentCompletion(
    val completionId: String,
    val choreName: String,
    val note: String?,
    val completedAt: Instant,
    val participantNames: List<String>,
)
