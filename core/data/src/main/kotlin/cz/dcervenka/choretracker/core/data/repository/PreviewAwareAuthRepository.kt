package cz.dcervenka.choretracker.core.data.repository

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.remote.contract.RemoteAuthDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Singleton
class PreviewAwareAuthRepository @Inject constructor(
    private val remoteAuthDataSource: RemoteAuthDataSource,
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val previewState = MutableStateFlow<AuthState?>(null)

    override val authState: Flow<AuthState> = combine(
        remoteAuthDataSource.authState,
        previewState,
    ) { remoteState, preview ->
        preview ?: remoteState
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = if (remoteAuthDataSource.isConfigured) AuthState.SignedOut else AuthState.RequiresConfiguration,
    )

    override suspend fun signIn(email: String, password: String): EmptyResult =
        remoteAuthDataSource.signIn(email, password)

    override suspend fun signUp(email: String, password: String, displayName: String): EmptyResult =
        remoteAuthDataSource.signUp(email, password, displayName)

    override suspend fun continueInPreviewMode(displayName: String): EmptyResult {
        previewState.value = AuthState.Authenticated(
            AppUser(
                id = "preview-user",
                email = null,
                displayName = displayName.ifBlank { "Preview User" },
                isPreview = true,
            ),
        )
        return AppResult.Success(Unit)
    }

    override suspend fun signOut(): EmptyResult {
        previewState.value = null
        return remoteAuthDataSource.signOut()
    }
}
