package cz.dcervenka.choretracker.core.model.auth

data class AppUser(
    val id: String,
    val email: String?,
    val displayName: String,
    val isPreview: Boolean = false,
)
