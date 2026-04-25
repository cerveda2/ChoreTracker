package cz.dcervenka.choretracker.core.test.mock

import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.household.Invite
import cz.dcervenka.choretracker.core.model.stats.DashboardSnapshot
import cz.dcervenka.choretracker.core.model.stats.HouseholdSummary
import cz.dcervenka.choretracker.core.model.stats.MemberContribution
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot
import kotlin.time.Instant

fun sampleHousehold(
    id: String = "household-1",
) = Household(
    id = id,
    name = "Home",
    ownerUserId = "user-1",
    inviteCode = "ABC123",
    createdAt = Instant.parse("2026-01-01T10:00:00Z"),
)

fun sampleAuthenticatedState() = AuthState.Authenticated(
    user = AppUser(
        id = "user-1",
        email = "dana@example.com",
        displayName = "Dana",
    ),
)

fun sampleMembers() = listOf(
    HouseholdMember(
        id = "member-1",
        householdId = "household-1",
        userId = "user-1",
        displayName = "Dana",
        role = HouseholdRole.OWNER,
        isCurrentUser = true,
    ),
    HouseholdMember(
        id = "member-2",
        householdId = "household-1",
        userId = "user-2",
        displayName = "Alex",
        role = HouseholdRole.MEMBER,
    ),
)

fun sampleInvite() = Invite(
    id = "invite-1",
    householdId = "household-1",
    code = "ABC123",
    createdAt = Instant.parse("2026-01-02T10:00:00Z"),
)

fun sampleChore() = Chore(
    id = "chore-1",
    householdId = "household-1",
    name = "Kitchen",
    isActive = true,
    createdAt = Instant.parse("2026-01-03T10:00:00Z"),
)

private val sampleContribution = MemberContribution(
    memberId = "member-1",
    displayName = "Dana",
    totalCount = 5,
    last30DaysCount = 3,
    currentMonthCount = 2,
    sharePercent = 100,
)

fun sampleDashboardSnapshot() = DashboardSnapshot(
    household = sampleHousehold(),
    summary = HouseholdSummary(totalCompletions = 5, topContributor = sampleContribution),
    memberContributions = listOf(sampleContribution),
    activeChores = emptyList(),
    recentCompletions = listOf(
        RecentCompletion(
            completionId = "completion-1",
            choreName = "Kitchen",
            note = null,
            completedAt = Instant.parse("2026-03-30T10:00:00Z"),
            participantNames = listOf("Dana"),
        ),
    ),
    staleChores = emptyList(),
)

fun sampleStatsSnapshot() = StatsSnapshot(
    household = sampleHousehold(),
    summary = HouseholdSummary(totalCompletions = 0, topContributor = null),
    memberContributions = emptyList(),
    comparisons = emptyList(),
    categoryComparisons = emptyList(),
    monthlyBreakdown = emptyList(),
    staleChores = emptyList(),
)
