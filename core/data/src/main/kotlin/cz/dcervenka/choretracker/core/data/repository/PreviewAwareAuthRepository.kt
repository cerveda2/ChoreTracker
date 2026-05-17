package cz.dcervenka.choretracker.core.data.repository

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.di.ApplicationScope
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.remote.contract.RemoteAuthDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreviewAwareAuthRepository @Inject constructor(
    private val remoteAuthDataSource: RemoteAuthDataSource,
    @ApplicationScope private val scope: CoroutineScope,
) : AuthRepository {

    private val previewState = MutableStateFlow<AuthState?>(null)

    override val authState: Flow<AuthState> = combine(
        remoteAuthDataSource.authState,
        previewState,
    ) { remoteState, preview ->
        preview ?: remoteState
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = if (remoteAuthDataSource.isConfigured) AuthState.Initializing else AuthState.RequiresConfiguration,
    )

    override suspend fun signIn(email: String, password: String): EmptyResult =
        remoteAuthDataSource.signIn(email, password)

    override suspend fun signUp(email: String, password: String, displayName: String): EmptyResult =
        remoteAuthDataSource.signUp(email, password, displayName)

    override suspend fun continueInPreviewMode(displayName: String): EmptyResult {
        previewState.value = null
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

    override suspend fun updateDisplayName(displayName: String): EmptyResult {
        val sanitizedName = displayName.trim()
        val previewUser = (previewState.value as? AuthState.Authenticated)?.user
        if (previewUser != null) {
            previewState.value = AuthState.Authenticated(
                previewUser.copy(displayName = sanitizedName.ifBlank { "Preview User" }),
            )
            return AppResult.Success(Unit)
        }
        return remoteAuthDataSource.updateDisplayName(sanitizedName)
    }

    override fun clearPreviewState() {
        previewState.value = null
    }

    override suspend fun signOut(): EmptyResult {
        previewState.value = null
        return remoteAuthDataSource.signOut()
    }
}
