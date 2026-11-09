package cz.dcervenka.choretracker.feature.dashboard.impl.contract

// "Remind {name}" on a due chore that's another member's turn: an ask for just that chore.
data class Nudge(
    val memberId: String,
    val memberName: String,
    val alreadyAsked: Boolean,
)
