package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.database.database.ChoreTrackerDatabase
import cz.dcervenka.choretracker.core.database.database.HOUSEHOLD_TABLES
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.database.entity.PersonalChoreEntity
import cz.dcervenka.choretracker.core.database.entity.PersonalCompletionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

// Robolectric 4.16.1's newest supported SDK is 36; targetSdk is 37, ahead of what
// Robolectric ships shadows for yet. Pin the test SDK explicitly until Robolectric catches up.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersonalChoreDaoTest {

    private lateinit var database: ChoreTrackerDatabase
    private lateinit var dao: PersonalChoreDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChoreTrackerDatabase::class.java,
        ).build()
        dao = database.personalChoreDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `getChores returns only the owner's chores that aren't deleted`() = runTest {
        dao.upsertChore(chore("gym", ownerUserId = "user-1"))
        dao.upsertChore(chore("meds", ownerUserId = "user-1", deletedAt = Instant.fromEpochMilliseconds(1)))
        dao.upsertChore(chore("other", ownerUserId = "user-2"))

        assertThat(dao.getChores("user-1").map { it.id }).containsExactly("gym")
        assertThat(dao.getChore("meds", "user-1")).isNotNull()
        assertThat(dao.getChore("gym", "user-2")).isNull()
    }

    @Test
    fun `completions are scoped to their owner and can be deleted`() = runTest {
        dao.upsertCompletion(completion("c-1", ownerUserId = "user-1"))
        dao.upsertCompletion(completion("c-2", ownerUserId = "user-2"))

        dao.deleteCompletion("c-1")

        assertThat(dao.getCompletions("user-1")).isEmpty()
        assertThat(dao.getCompletions("user-2").map { it.id }).containsExactly("c-2")
    }

    @Test
    fun `clearAll wipes personal chores and completions`() = runTest {
        dao.upsertChore(chore("gym", ownerUserId = "user-1"))
        dao.upsertCompletion(completion("c-1", ownerUserId = "user-1"))

        database.clearAll()

        assertThat(dao.getChores("user-1")).isEmpty()
        assertThat(dao.getCompletions("user-1")).isEmpty()
    }

    @Test
    fun `clearHouseholdData keeps personal chores and their queued uploads`() = runTest {
        dao.upsertChore(chore("gym", ownerUserId = "user-1"))
        dao.upsertCompletion(completion("c-1", ownerUserId = "user-1"))
        database.pendingSyncOperationDao().upsert(operation("op-personal", "personal_chore"))
        database.pendingSyncOperationDao().upsert(operation("op-household", "chore"))

        database.clearHouseholdData()

        assertThat(dao.getChores("user-1").map { it.id }).containsExactly("gym")
        assertThat(dao.getCompletions("user-1").map { it.id }).containsExactly("c-1")
        assertThat(database.pendingSyncOperationDao().getAll().map { it.id }).containsExactly("op-personal")
    }

    @Test
    fun `clearHouseholdData wipes every table but the personal ones and the sync queue`() {
        val tables = database.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        val kept = setOf(
            "android_metadata",
            "room_master_table",
            "personal_chores",
            "personal_completions",
            "pending_sync_operations",
        )

        assertThat(tables - kept).containsExactlyElementsIn(HOUSEHOLD_TABLES)
    }

    private fun operation(id: String, entityType: String) = PendingSyncOperationEntity(
        id = id,
        entityType = entityType,
        entityId = id,
        operationType = "upsert",
        payload = "",
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    private fun chore(id: String, ownerUserId: String, deletedAt: Instant? = null) = PersonalChoreEntity(
        id = id,
        ownerUserId = ownerUserId,
        name = id,
        isActive = true,
        createdAt = Instant.fromEpochMilliseconds(0),
        deletedAt = deletedAt,
    )

    private fun completion(id: String, ownerUserId: String) = PersonalCompletionEntity(
        id = id,
        ownerUserId = ownerUserId,
        choreId = "gym",
        createdAt = Instant.fromEpochMilliseconds(0),
        note = null,
    )
}
