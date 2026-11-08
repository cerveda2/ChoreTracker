package cz.dcervenka.choretracker.core.data.contract

import kotlinx.coroutines.flow.Flow

interface NotificationsBannerRepository {
    fun observeDismissed(): Flow<Boolean>

    suspend fun dismiss()
}
