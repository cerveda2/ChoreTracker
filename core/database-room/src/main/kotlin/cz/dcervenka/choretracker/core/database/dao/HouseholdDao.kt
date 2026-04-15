package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseholdDao {
    @Query("SELECT * FROM households ORDER BY createdAt DESC LIMIT 1")
    fun observeCurrentHousehold(): Flow<HouseholdEntity?>

    @Query("SELECT * FROM households ORDER BY createdAt DESC LIMIT 1")
    suspend fun getCurrentHousehold(): HouseholdEntity?

    @Query(
        """
        SELECT h.* FROM households h
        INNER JOIN members m ON h.id = m.householdId
        WHERE m.userId = :userId
        ORDER BY h.createdAt DESC LIMIT 1
        """,
    )
    fun observeHouseholdForUser(userId: String): Flow<HouseholdEntity?>

    @Query(
        """
        SELECT h.* FROM households h
        INNER JOIN members m ON h.id = m.householdId
        WHERE m.userId = :userId
        ORDER BY h.createdAt DESC LIMIT 1
        """,
    )
    suspend fun getCurrentHouseholdForUser(userId: String): HouseholdEntity?

    @Query("SELECT * FROM households WHERE id = :householdId LIMIT 1")
    fun observeHousehold(householdId: String): Flow<HouseholdEntity?>

    @Query("SELECT * FROM households WHERE id = :householdId LIMIT 1")
    suspend fun getHousehold(householdId: String): HouseholdEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HouseholdEntity)

    @Query("UPDATE households SET inviteCode = :inviteCode WHERE id = :householdId")
    suspend fun updateInviteCode(householdId: String, inviteCode: String)

    @Query("UPDATE households SET name = :name WHERE id = :householdId")
    suspend fun updateName(householdId: String, name: String)
}
