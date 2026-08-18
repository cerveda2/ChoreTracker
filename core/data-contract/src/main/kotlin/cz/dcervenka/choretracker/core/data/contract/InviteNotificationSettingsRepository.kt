package cz.dcervenka.choretracker.core.data.contract

import kotlinx.coroutines.flow.Flow

interface InviteNotificationSettingsRepository {
    fun observeEnabled(userId: String): Flow<Boolean>

    suspend fun isEnabled(userId: String): Boolean

    suspend fun setEnabled(userId: String, enabled: Boolean)
}
