package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import org.junit.Test

class BalanceCardTest {

    @Test
    fun `leader who isn't the first member is ordered first`() {
        val administrator = member("member-1", "Administrator")
        val sam = member("member-2", "Sam")

        val ordered = leaderFirstOrder(
            members = listOf(administrator, sam),
            leaderId = sam.id,
            laggingId = administrator.id,
            countsByMemberId = mapOf(administrator.id to 10, sam.id to 11),
        )

        assertThat(ordered).containsExactly(sam, administrator).inOrder()
    }

    @Test
    fun `three members put leader first, lagging last and the rest by count`() {
        val lagging = member("member-1", "Dana")
        val middleLow = member("member-2", "Alex")
        val middleHigh = member("member-3", "Kim")
        val leader = member("member-4", "Sam")

        val ordered = leaderFirstOrder(
            members = listOf(lagging, middleLow, middleHigh, leader),
            leaderId = leader.id,
            laggingId = lagging.id,
            countsByMemberId = mapOf(lagging.id to 2, middleLow.id to 4, middleHigh.id to 6, leader.id to 9),
        )

        assertThat(ordered).containsExactly(leader, middleHigh, middleLow, lagging).inOrder()
    }

    @Test
    fun `leader tied with another member stays first`() {
        val first = member("member-1", "Dana")
        val leader = member("member-2", "Alex")
        val lagging = member("member-3", "Kim")

        val ordered = leaderFirstOrder(
            members = listOf(first, leader, lagging),
            leaderId = leader.id,
            laggingId = lagging.id,
            countsByMemberId = mapOf(first.id to 5, leader.id to 5, lagging.id to 1),
        )

        assertThat(ordered).containsExactly(leader, first, lagging).inOrder()
    }

    private fun member(id: String, name: String) = HouseholdMember(
        id = id,
        householdId = "household-1",
        userId = "user-$id",
        displayName = name,
        role = HouseholdRole.MEMBER,
    )
}
