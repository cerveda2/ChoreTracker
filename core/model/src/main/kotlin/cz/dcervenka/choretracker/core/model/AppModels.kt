package cz.dcervenka.choretracker.core.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class AppUser(
    val id: String,
    val email: String?,
    val displayName: String,
    val isPreview: Boolean = false,
)

sealed interface AuthState {
    data object SignedOut : AuthState
    data object RequiresConfiguration : AuthState
    data class Authenticated(val user: AppUser) : AuthState
}

data class Household(
    val id: String,
    val name: String,
    val ownerUserId: String,
    val inviteCode: String,
    val createdAt: Instant,
)

enum class HouseholdRole {
    OWNER,
    MEMBER,
}

data class HouseholdMember(
    val id: String,
    val householdId: String,
    val userId: String?,
    val displayName: String,
    val role: HouseholdRole,
    val isCurrentUser: Boolean = false,
)

data class Chore(
    val id: String,
    val householdId: String,
    val name: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val deletedAt: Instant? = null,
)

data class ChoreCompletion(
    val id: String,
    val householdId: String,
    val choreId: String,
    val createdAt: Instant,
    val createdByUserId: String,
    val note: String?,
    val participantMemberIds: List<String>,
)

data class Invite(
    val id: String,
    val householdId: String,
    val code: String,
    val createdAt: Instant,
    val consumedAt: Instant? = null,
)

data class PendingSyncOperation(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payload: String,
    val createdAt: Instant,
)

data class SyncState(
    val householdId: String,
    val lastSyncedAt: Instant?,
    val pendingOperations: Int,
)

data class MemberContribution(
    val memberId: String,
    val displayName: String,
    val totalCount: Int,
    val last30DaysCount: Int,
    val currentMonthCount: Int,
)

data class RecentCompletion(
    val completionId: String,
    val choreName: String,
    val note: String?,
    val completedAt: Instant,
    val participantNames: List<String>,
)

data class ChoreComparison(
    val choreId: String,
    val choreName: String,
    val countsByMember: Map<String, Int>,
    val leaderLabel: String,
    val totalCount: Int,
)

data class ChoreStaleness(
    val choreId: String,
    val choreName: String,
    val lastCompletedDate: LocalDate?,
    val daysSinceLastCompletion: Int?,
    val status: String,
)

data class DashboardSnapshot(
    val household: Household,
    val memberContributions: List<MemberContribution>,
    val activeChores: List<Chore>,
    val recentCompletions: List<RecentCompletion>,
    val staleChores: List<ChoreStaleness>,
)

data class MonthlyBreakdown(
    val monthLabel: String,
    val countsByMember: Map<String, Int>,
    val totalCount: Int,
)

data class StatsSnapshot(
    val household: Household,
    val comparisons: List<ChoreComparison>,
    val monthlyBreakdown: List<MonthlyBreakdown>,
    val staleChores: List<ChoreStaleness>,
)
