package cz.dcervenka.choretracker.core.data.repository

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.database.dao.HouseholdDao
import cz.dcervenka.choretracker.core.database.dao.InviteDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.database.ChoreTrackerDatabase
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
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

    @MockK
    lateinit var database: ChoreTrackerDatabase

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
        every { householdDao.observeHouseholdForUser(any()) } returns localHousehold
        coEvery { householdDao.getCurrentHousehold() } returns null
        coEvery { householdDao.getCurrentHouseholdForUser(any()) } returns null
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
            database = database,
        )
    }

    @Test
    fun `observeCurrentHousehold restores remote data before emitting null for authenticated user`() = runBlocking {
        assertThat(repository.observeCurrentHousehold().first()?.id).isEqualTo("household-1")
        coVerify(exactly = 1) { syncRepository.restoreHouseholdForUser("user-1") }
    }

    @Test
    fun `observeCurrentHousehold stamps member displayName when auth displayName differs`() = runBlocking {
        val household = HouseholdEntity(
            id = "household-1",
            name = "Home",
            ownerUserId = "user-1",
            inviteCode = "ABC123",
            createdAt = Instant.parse("2026-01-01T10:00:00Z"),
        )
        val member = MemberEntity(
            id = "member-1",
            householdId = "household-1",
            userId = "user-1",
            displayName = "OldName",
            role = HouseholdRole.OWNER.name,
            isCurrentUser = true,
            email = "dana@example.com",
        )
        authState.value = AuthState.Authenticated(AppUser("user-1", "dana@example.com", "NewName"))
        coEvery { householdDao.getCurrentHouseholdForUser("user-1") } returns household
        coEvery { memberDao.findByUserId("household-1", "user-1") } returns member
        coEvery { memberDao.upsert(any()) } just Runs
        coEvery { pendingSyncOperationDao.upsert(any()) } just Runs
        localHousehold.value = household

        repository.observeCurrentHousehold().first()

        coVerify { memberDao.upsert(match { it.id == "member-1" && it.displayName == "NewName" }) }
    }

    @Test
    fun `observeCurrentHousehold skips displayName stamp when auth displayName is email fallback`() = runBlocking {
        authState.value = AuthState.Authenticated(
            AppUser("user-1", "dana@example.com", "dana@example.com"),
        )

        repository.observeCurrentHousehold().first()

        coVerify(exactly = 0) { memberDao.upsert(match { it.displayName == "dana@example.com" }) }
    }

    @Test
    fun `joinHousehold claims placeholder member with joining user displayName`() = runBlocking {
        val household = HouseholdEntity(
            id = "household-1",
            name = "Home",
            ownerUserId = "other-user",
            inviteCode = "ABCD1234",
            createdAt = Instant.parse("2026-01-01T10:00:00Z"),
        )
        val invite = InviteEntity(
            id = "invite-1",
            householdId = "household-1",
            code = "ABCD1234",
            createdAt = Instant.parse("2026-01-01T10:00:00Z"),
            consumedAt = null,
            targetMemberId = "placeholder-id",
            consumedByMemberId = null,
        )
        val placeholder = MemberEntity(
            id = "placeholder-id",
            householdId = "household-1",
            userId = null,
            displayName = "OwnerAssignedName",
            role = HouseholdRole.MEMBER.name,
            isCurrentUser = false,
        )
        val claimedMember = placeholder.copy(userId = "user-1", isCurrentUser = true)
        coEvery { inviteDao.findByCode("ABCD1234") } returns invite
        coEvery { memberDao.findById("household-1", "placeholder-id") } returns placeholder
        coEvery { memberDao.claimPlaceholder(any(), any(), any(), any()) } just Runs
        coEvery { memberDao.findByUserId("household-1", "user-1") } returns claimedMember
        coEvery { inviteDao.markConsumed(any(), any(), any()) } just Runs
        coEvery { pendingSyncOperationDao.upsert(any()) } just Runs
        coEvery { householdDao.getHousehold("household-1") } returns household

        val result = repository.joinHousehold("ABCD1234", "UserTypedName")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify {
            memberDao.claimPlaceholder("placeholder-id", "user-1", "dana@example.com", "UserTypedName")
        }
    }
}
