package cz.dcervenka.choretracker.core.model.household

enum class HouseholdRole {
    OWNER,
    ADMIN,
    MEMBER,
    ;

    // Owners and admins manage the household - invites, chores, members.
    val isManager: Boolean
        get() = this == OWNER || this == ADMIN
}
