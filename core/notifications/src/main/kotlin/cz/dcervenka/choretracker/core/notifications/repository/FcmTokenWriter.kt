package cz.dcervenka.choretracker.core.notifications.repository

interface FcmTokenWriter {
    suspend fun requestRegistration()
    suspend fun writeToken(userId: String, token: String)
    suspend fun clearToken(userId: String)
}
