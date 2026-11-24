package cz.dcervenka.choretracker.feature.chores.impl.contract

import cz.dcervenka.choretracker.core.common.UiIntent
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreSchedule
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import kotlin.time.Instant

sealed interface ChoresUiIntent : UiIntent {
    data class FilterChanged(val filter: ChoreFilter) : ChoresUiIntent
    data class QueryChanged(val value: String) : ChoresUiIntent
    data class AddChore(
        val householdId: String,
        val name: String,
        val category: ChoreCategory,
        val schedule: ChoreSchedule?,
        val reminderEnabled: Boolean,
        val rotationMemberIds: List<String> = emptyList(),
        val groupName: String? = null,
        val personal: Boolean = false,
    ) : ChoresUiIntent
    data class UpdateChore(
        val choreId: String,
        val name: String,
        val category: ChoreCategory,
        val schedule: ChoreSchedule?,
        val isActive: Boolean,
        val rotationMemberIds: List<String> = emptyList(),
        val groupName: String? = null,
    ) : ChoresUiIntent
    data class DeleteChore(val choreId: String) : ChoresUiIntent
    data class LogCompletion(
        val householdId: String,
        val choreId: String,
        val participantIds: List<String>,
        val note: String?,
        val completedAt: Instant?,
    ) : ChoresUiIntent
    data class DeleteCompletion(val completionId: String) : ChoresUiIntent
    data class SetChoreReminder(val choreId: String, val enabled: Boolean) : ChoresUiIntent
    data class SendRequests(
        val householdId: String,
        val toMember: HouseholdMember,
        val choreIds: List<String>,
        val note: String,
    ) : ChoresUiIntent
}
