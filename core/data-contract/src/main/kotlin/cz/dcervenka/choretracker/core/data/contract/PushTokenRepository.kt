package cz.dcervenka.choretracker.core.data.contract

interface PushTokenRepository {
    suspend fun clearCurrentUserToken()
}
