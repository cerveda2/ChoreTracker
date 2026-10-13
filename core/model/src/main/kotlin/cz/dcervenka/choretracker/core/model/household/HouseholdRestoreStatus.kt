package cz.dcervenka.choretracker.core.model.household

import cz.dcervenka.choretracker.core.model.error.AppError

data class HouseholdRestoreStatus(
    val isRestoring: Boolean = false,
    val error: AppError? = null,
)
