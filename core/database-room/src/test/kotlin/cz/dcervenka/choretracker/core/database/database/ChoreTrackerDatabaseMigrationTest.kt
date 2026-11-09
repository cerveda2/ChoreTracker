package cz.dcervenka.choretracker.core.database.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.database.di.DatabaseModule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val DATABASE_NAME = "chore-tracker.db"

// The oldest schema a released build can have on disk, and the oldest one exported.
private const val BASELINE_VERSION = 10

// Devices on a shipped version hold its hash, so a rewritten export must fail here, not crash them on open.
// Add each version here once a release ships it.
private val SHIPPED_IDENTITY_HASHES = mapOf(
    BASELINE_VERSION to "906948c5bcf252996be9593c76217b79",
    11 to "d69d27eee21dc91313d5d6526d0e4645",
)

// Robolectric 4.16.1's newest supported SDK is 36; targetSdk is 37, ahead of what
// Robolectric ships shadows for yet. Pin the test SDK explicitly until Robolectric catches up.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChoreTrackerDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChoreTrackerDatabase::class.java,
    )

    @Test
    fun `baseline schema opens from its export and validates`() {
        helper.createDatabase(DATABASE_NAME, BASELINE_VERSION).close()

        helper.runMigrationsAndValidate(DATABASE_NAME, BASELINE_VERSION, validateDroppedTables = true).close()
    }

    @Test
    fun `shipped schema exports are unchanged`() {
        SHIPPED_IDENTITY_HASHES.forEach { (version, identityHash) ->
            helper.createDatabase("$DATABASE_NAME-$version", version).use { db ->
                db.query("SELECT identity_hash FROM room_master_table").use { cursor ->
                    cursor.moveToFirst()
                    assertThat(cursor.getString(0)).isEqualTo(identityHash)
                }
            }
        }
    }

    // Goes through the production builder, so a version bump without a registered migration fails here.
    // Keep the seed on the baseline schema; a 10-to-11 migration adds assertions on what it must preserve.
    @Test
    fun `production database opens the baseline schema without losing unsynced writes`() = runTest {
        helper.createDatabase(DATABASE_NAME, BASELINE_VERSION).use { db ->
            db.execSQL(
                "INSERT INTO pending_sync_operations " +
                    "(id, entityType, entityId, operationType, payload, createdAt) " +
                    "VALUES ('op-1', 'chore', 'chore-1', 'upsert', '{}', 0)",
            )
            db.execSQL(
                "INSERT INTO chores (id, householdId, name, isActive, createdAt, deletedAt, frequencyDays, category) " +
                    "VALUES ('chore-1', 'household-1', 'Dishes', 1, 0, NULL, NULL, 'OTHER')",
            )
        }

        val database = DatabaseModule.provideDatabase(ApplicationProvider.getApplicationContext())
        val pending = database.pendingSyncOperationDao().getAll()
        val legacyChore = database.choreDao().getChore("chore-1")
        database.close()

        assertThat(pending.map { it.id }).containsExactly("op-1")
        assertThat(legacyChore?.createdByUserId).isNull()
        assertThat(legacyChore?.rotationMemberIds).isEmpty()
        assertThat(legacyChore?.rotationStartedAt).isNull()
    }
}
