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
    data class UpdateCompletion(
        val completionId: String,
        val note: String?,
        val participantIds: List<String>,
    ) : DashboardUiIntent
    data class DeleteCompletion(val completionId: String) : DashboardUiIntent
    data object RetrySync : DashboardUiIntent
    data object Refresh : DashboardUiIntent
    data object DismissNotificationsBanner : DashboardUiIntent
    data class SystemNotificationsChanged(val enabled: Boolean) : DashboardUiIntent
    data class NetworkAvailabilityChanged(val available: Boolean) : DashboardUiIntent
    data class DeclineRequest(val requestId: String) : DashboardUiIntent
    data class CancelRequest(val requestId: String) : DashboardUiIntent
    data class Remind(val choreId: String) : DashboardUiIntent
}
