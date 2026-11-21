package cz.dcervenka.choretracker.core.data.contract

interface UpdatePromptRepository {
    suspend fun getLastOfferedVersion(): Int?

    suspend fun setLastOfferedVersion(versionCode: Int)
}
