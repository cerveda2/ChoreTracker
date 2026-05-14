package cz.dcervenka.choretracker.core.model.household

data class HouseholdMember(
    val id: String,
    val householdId: String,
    val userId: String?,
    val displayName: String,
    val role: HouseholdRole,
    val isCurrentUser: Boolean = false,
    val email: String? = null,
)
