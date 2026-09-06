package cz.dcervenka.choretracker.core.sync.repository

import cz.dcervenka.choretracker.core.model.household.HouseholdMember

/**
 * Collapses a claimed member and its pre-join placeholder (same logical id, one with a userId
 * and one without) into a single row, preferring the placeholder's owner-assigned display name
 * for a brief window right after a join. Shared by LocalSyncRepository's push/pull path
 * (applySnapshot) and RealtimeSyncApplier's real-time path (applyMembers) - both can observe the
 * same transient dual-doc state.
 */
internal fun deduplicateMembers(members: List<HouseholdMember>): List<HouseholdMember> =
    members.groupBy { it.id }.values.map { group ->
        val claimed = group.firstOrNull { it.userId != null }
        val placeholder = group.firstOrNull { it.userId == null }
        when {
            claimed != null && placeholder != null ->
                claimed.copy(displayName = placeholder.displayName.ifBlank { claimed.displayName })
            else -> group.maxByOrNull { if (it.userId != null) 1 else 0 }!!
        }
    }
