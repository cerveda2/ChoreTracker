package cz.dcervenka.choretracker.feature.dashboard.impl.contract

import cz.dcervenka.choretracker.core.common.UiIntent
import kotlin.time.Instant

sealed interface DashboardUiIntent : UiIntent {
    data class LogCompletion(
        val householdId: String,
        val choreId: String,
        val participantIds: List<String>,
        val note: String?,
        val completedAt: Instant?,
    ) : DashboardUiIntent
    data class DeleteCompletion(val completionId: String) : DashboardUiIntent
    data object RetrySync : DashboardUiIntent
}
