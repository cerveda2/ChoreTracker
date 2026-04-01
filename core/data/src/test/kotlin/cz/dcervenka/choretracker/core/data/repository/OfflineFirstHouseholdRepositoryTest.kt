package cz.dcervenka.choretracker.core.data.repository

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.database.dao.HouseholdDao
import cz.dcervenka.choretracker.core.database.dao.InviteDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class OfflineFirstHouseholdRepositoryTest {

    @MockK
    lateinit var householdDao: HouseholdDao

    @MockK
    lateinit var memberDao: MemberDao

    @MockK
    lateinit var inviteDao: InviteDao

    @MockK
    lateinit var pendingSyncOperationDao: PendingSyncOperationDao

    @MockK
    lateinit var authRepository: AuthRepository

    @MockK
    lateinit var syncRepository: SyncRepository

    private val authState = MutableStateFlow<AuthState>(
        AuthState.Authenticated(
            user = AppUser(
                id = "user-1",
                email = "dana@example.com",
                displayName = "Dana",
            ),
        ),
    )
    private val localHousehold = MutableStateFlow<HouseholdEntity?>(null)

    private lateinit var repository: OfflineFirstHouseholdRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { authRepository.authState } returns authState
        every { householdDao.observeCurrentHousehold() } returns localHousehold
        coEvery { householdDao.getCurrentHousehold() } returns null
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Success(Unit)
        coEvery { syncRepository.restoreHouseholdForUser("user-1") } coAnswers {
            localHousehold.value = HouseholdEntity(
                id = "household-1",
                name = "Home",
                ownerUserId = "user-1",
                inviteCode = "ABC123",
                createdAt = Instant.parse("2026-01-01T10:00:00Z"),
            )
            AppResult.Success(true)
        }
        repository = OfflineFirstHouseholdRepository(
            householdDao = householdDao,
            memberDao = memberDao,
            inviteDao = inviteDao,
            pendingSyncOperationDao = pendingSyncOperationDao,
            authRepository = authRepository,
            syncRepository = syncRepository,
        )
    }

    @Test
    fun `observeCurrentHousehold restores remote data before emitting null for authenticated user`() = runBlocking {
        assertThat(repository.observeCurrentHousehold().first()?.id).isEqualTo("household-1")
        coVerify(exactly = 1) { syncRepository.restoreHouseholdForUser("user-1") }
    }
}
