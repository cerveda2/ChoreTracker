package cz.dcervenka.choretracker.core.model.household

data class HouseholdRestoreStatus(
    val isRestoring: Boolean = false,
    val errorMessage: String? = null,
)
