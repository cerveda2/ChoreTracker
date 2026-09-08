package cz.dcervenka.choretracker.feature.chores.impl.contract

import cz.dcervenka.choretracker.core.common.UiIntent
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import kotlin.time.Instant

sealed interface ChoresUiIntent : UiIntent {
    data class FilterChanged(val filter: ChoreFilter) : ChoresUiIntent
    data class QueryChanged(val value: String) : ChoresUiIntent
    data class AddChore(
        val householdId: String,
        val name: String,
        val category: ChoreCategory,
        val frequencyDays: Int?,
    ) : ChoresUiIntent
    data class UpdateChore(
        val choreId: String,
        val name: String,
        val category: ChoreCategory,
        val frequencyDays: Int?,
        val isActive: Boolean,
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
}
