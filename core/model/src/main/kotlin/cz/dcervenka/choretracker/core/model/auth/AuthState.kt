package cz.dcervenka.choretracker.core.model.auth

sealed interface AuthState {
    data object SignedOut : AuthState
    data object RequiresConfiguration : AuthState
    data class Authenticated(val user: AppUser) : AuthState
}
