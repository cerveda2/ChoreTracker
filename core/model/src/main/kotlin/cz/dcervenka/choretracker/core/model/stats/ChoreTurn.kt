package cz.dcervenka.choretracker.core.model.stats

data class ChoreTurn(
    val memberId: String,
    val memberName: String,
    val isCurrentUser: Boolean,
)
