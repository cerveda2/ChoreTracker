package cz.dcervenka.choretracker.core.model.chore

import kotlin.time.Instant

// A request as Home shows it. The other member is the sender for one asked of you, and the recipient
// for one you're waiting on.
data class ChoreRequestItem(
    val id: String,
    val choreId: String,
    val choreName: String,
    val category: ChoreCategory,
    val otherMemberId: String,
    val otherMemberName: String,
    val note: String?,
    val createdAt: Instant,
    val declined: Boolean = false,
)

data class ChoreRequests(
    val askedOfMe: List<ChoreRequestItem> = emptyList(),
    val waitingOn: List<ChoreRequestItem> = emptyList(),
)
