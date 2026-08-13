package cz.dcervenka.choretracker.core.data.contract

import kotlinx.coroutines.flow.Flow

interface InviteNotificationSettingsRepository {
    fun observeEnabled(): Flow<Boolean>

    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)
}
