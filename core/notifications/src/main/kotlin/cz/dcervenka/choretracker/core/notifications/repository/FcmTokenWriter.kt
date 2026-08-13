package cz.dcervenka.choretracker.core.notifications.repository

interface FcmTokenWriter {
    suspend fun fetchCurrentDeviceToken(): String?
    suspend fun writeToken(userId: String, token: String)
    suspend fun clearToken(userId: String)
}
