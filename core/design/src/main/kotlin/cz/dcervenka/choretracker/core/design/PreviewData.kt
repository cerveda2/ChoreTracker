package cz.dcervenka.choretracker.core.design

import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.household.Invite
import cz.dcervenka.choretracker.core.model.stats.ChoreComparison
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.DashboardSnapshot
import cz.dcervenka.choretracker.core.model.stats.MemberContribution
import cz.dcervenka.choretracker.core.model.stats.MonthlyBreakdown
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

object PreviewData {
    private val now = Clock.System.now()

    val household = Household(
        id = "household-preview",
        name = "Sunny Flat",
        ownerUserId = "user-1",
        inviteCode = "HOME42",
        createdAt = now,
    )

    val members = listOf(
        HouseholdMember(
            id = "member-1",
            householdId = household.id,
            userId = "user-1",
            displayName = "Dana",
            role = HouseholdRole.OWNER,
            isCurrentUser = true,
        ),
        HouseholdMember(
            id = "member-2",
            householdId = household.id,
            userId = "user-2",
            displayName = "Alex",
            role = HouseholdRole.MEMBER,
        ),
    )

    val chores = listOf(
        Chore(
            id = "chore-1",
            householdId = household.id,
            name = "Kitchen cleanup",
            isActive = true,
            createdAt = now,
        ),
        Chore(
            id = "chore-2",
            householdId = household.id,
            name = "Laundry",
            isActive = true,
            createdAt = now,
        ),
        Chore(
            id = "chore-3",
            householdId = household.id,
            name = "Plants",
            isActive = false,
            createdAt = now,
        ),
    )

    val invite = Invite(
        id = "invite-1",
        householdId = household.id,
        code = household.inviteCode,
        createdAt = now,
    )

    val dashboardSnapshot = DashboardSnapshot(
        household = household,
        memberContributions = listOf(
            MemberContribution(
                memberId = "member-1",
                displayName = "Dana",
                totalCount = 28,
                last30DaysCount = 12,
                currentMonthCount = 7,
            ),
            MemberContribution(
                memberId = "member-2",
                displayName = "Alex",
                totalCount = 21,
                last30DaysCount = 10,
                currentMonthCount = 6,
            ),
        ),
        activeChores = chores.filter { it.isActive },
        recentCompletions = listOf(
            RecentCompletion(
                completionId = "completion-1",
                choreName = "Kitchen cleanup",
                note = "After dinner",
                completedAt = now,
                participantNames = listOf("Dana"),
            ),
            RecentCompletion(
                completionId = "completion-2",
                choreName = "Laundry",
                note = null,
                completedAt = now,
                participantNames = listOf("Dana", "Alex"),
            ),
        ),
        staleChores = listOf(
            ChoreStaleness(
                choreId = "chore-2",
                choreName = "Laundry",
                lastCompletedDate = LocalDate.parse("2026-03-22"),
                daysSinceLastCompletion = 7,
                status = "Soon",
            ),
            ChoreStaleness(
                choreId = "chore-3",
                choreName = "Plants",
                lastCompletedDate = LocalDate.parse("2026-03-10"),
                daysSinceLastCompletion = 19,
                status = "Overdue",
            ),
        ),
    )

    val statsSnapshot = StatsSnapshot(
        household = household,
        comparisons = listOf(
            ChoreComparison(
                choreId = "chore-1",
                choreName = "Kitchen cleanup",
                countsByMember = mapOf("Dana" to 14, "Alex" to 11),
                leaderLabel = "Dana",
                totalCount = 25,
            ),
            ChoreComparison(
                choreId = "chore-2",
                choreName = "Laundry",
                countsByMember = mapOf("Dana" to 8, "Alex" to 10),
                leaderLabel = "Alex",
                totalCount = 18,
            ),
        ),
        monthlyBreakdown = listOf(
            MonthlyBreakdown(
                monthLabel = "March 2026",
                countsByMember = mapOf("Dana" to 7, "Alex" to 6),
                totalCount = 13,
            ),
            MonthlyBreakdown(
                monthLabel = "February 2026",
                countsByMember = mapOf("Dana" to 5, "Alex" to 4),
                totalCount = 9,
            ),
        ),
        staleChores = dashboardSnapshot.staleChores,
    )
}
