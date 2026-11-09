package cz.dcervenka.choretracker.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.domain.HouseholdStatisticsCalculator
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreRequestItem
import cz.dcervenka.choretracker.core.model.chore.ChoreRequests
import cz.dcervenka.choretracker.core.model.error.AppError
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.core.model.stats.ChoreTurn
import cz.dcervenka.choretracker.core.model.stats.HouseholdStatsInput
import cz.dcervenka.choretracker.core.test.clock.FixedClock
import cz.dcervenka.choretracker.core.test.mock.sampleAuthenticatedState
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

private fun staleness(choreId: String, status: ChoreStatus) = ChoreStaleness(
    choreId = choreId,
    choreName = choreId,
    lastCompletedDate = null,
    daysSinceLastCompletion = null,
    frequencyDays = null,
    status = status,
)

private val emptyStatsInput = HouseholdStatsInput(
    household = sampleHousehold(),
    members = emptyList(),
    chores = emptyList(),
    completions = emptyList(),
)

class CheckStaleChoresUseCaseTest {

    @MockK
    lateinit var authRepository: AuthRepository

    @MockK
    lateinit var refreshHouseholdUseCase: RefreshHouseholdUseCase

    @MockK
    lateinit var householdRepository: HouseholdRepository

    @MockK
    lateinit var statsRepository: StatsRepository

    @MockK
    lateinit var statisticsCalculator: HouseholdStatisticsCalculator

    @MockK
    lateinit var observeChoreRequestsUseCase: ObserveChoreRequestsUseCase

    private val authStateFlow = MutableStateFlow<AuthState>(sampleAuthenticatedState())

    // A FixedClock lets the injected `today` be asserted exactly, derived the same way
    // production code derives it (rather than a hardcoded LocalDate literal), so this stays
    // correct regardless of the test runner's default timezone.
    private val fixedInstant = Instant.parse("2026-03-15T12:00:00Z")
    private val clock = FixedClock(fixedInstant)
    private val expectedToday = fixedInstant.toLocalDateTime(TimeZone.currentSystemDefault()).date

    private lateinit var useCase: CheckStaleChoresUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { authRepository.authState } returns authStateFlow
        coEvery { refreshHouseholdUseCase() } returns AppResult.Success(Unit)
        coEvery { householdRepository.getCurrentHousehold() } returns sampleHousehold()
        coEvery { statsRepository.getHouseholdStatsInput(any()) } returns emptyStatsInput
        every { statisticsCalculator.buildStaleness(any(), any(), any(), any(), expectedToday) } returns emptyList()
        every { observeChoreRequestsUseCase(any()) } returns flowOf(ChoreRequests())
        useCase = CheckStaleChoresUseCase(
            authRepository,
            refreshHouseholdUseCase,
            householdRepository,
            statsRepository,
            statisticsCalculator,
            observeChoreRequestsUseCase,
            clock,
        )
    }

    @Test
    fun `returns empty list for a preview user without touching any repository`() = runTest {
        authStateFlow.value = AuthState.Authenticated(
            AppUser(id = "preview-user", email = null, displayName = "Preview User", isPreview = true),
        )

        val result = useCase()

        assertThat(result).isEmpty()
        coVerify(exactly = 0) { refreshHouseholdUseCase() }
        coVerify(exactly = 0) { householdRepository.getCurrentHousehold() }
        coVerify(exactly = 0) { statsRepository.getHouseholdStatsInput(any()) }
    }

    @Test
    fun `returns empty list when signed out`() = runTest {
        authStateFlow.value = AuthState.SignedOut

        val result = useCase()

        assertThat(result).isEmpty()
        coVerify(exactly = 0) { refreshHouseholdUseCase() }
    }

    @Test
    fun `still checks staleness from local data when the remote sync fails`() = runTest {
        coEvery { refreshHouseholdUseCase() } returns AppResult.Error(AppError.NETWORK)
        coEvery { statsRepository.getHouseholdStatsInput("household-1") } returns emptyStatsInput
        every { statisticsCalculator.buildStaleness(any(), any(), any(), any(), expectedToday) } returns
            listOf(staleness("chore-1", ChoreStatus.NEEDS_ATTENTION))

        val result = useCase()

        coVerify(exactly = 1) { refreshHouseholdUseCase() }
        coVerify(exactly = 1) { statsRepository.getHouseholdStatsInput("household-1") }
        assertThat(result).hasSize(1)
    }

    @Test
    fun `returns empty list when there is no current household`() = runTest {
        coEvery { householdRepository.getCurrentHousehold() } returns null

        val result = useCase()

        assertThat(result).isEmpty()
        coVerify(exactly = 0) { statsRepository.getHouseholdStatsInput(any()) }
    }

    @Test
    fun `filters out chores that are not yet stale, including never-completed ones`() = runTest {
        coEvery { statsRepository.getHouseholdStatsInput("household-1") } returns emptyStatsInput
        every { statisticsCalculator.buildStaleness(any(), any(), any(), any(), expectedToday) } returns listOf(
            staleness("chore-1", ChoreStatus.NEEDS_ATTENTION),
            staleness("chore-2", ChoreStatus.SOON),
            staleness("chore-3", ChoreStatus.OK),
            staleness("chore-4", ChoreStatus.NEVER),
        )

        val result = useCase()

        assertThat(result.map(ChoreStaleness::choreId)).containsExactly("chore-1")
    }

    @Test
    fun `reminds only the member whose turn it is`() = runTest {
        every { statisticsCalculator.buildStaleness(any(), any(), any(), any(), expectedToday) } returns listOf(
            staleness("mine", ChoreStatus.NEEDS_ATTENTION).copy(turn = ChoreTurn("me", "Me", isCurrentUser = true)),
            staleness("theirs", ChoreStatus.NEEDS_ATTENTION).copy(turn = ChoreTurn("sam", "Sam", isCurrentUser = false)),
            staleness("shared", ChoreStatus.NEEDS_ATTENTION),
        )

        val result = useCase()

        assertThat(result.map(ChoreStaleness::choreId)).containsExactly("mine", "shared")
    }

    @Test
    fun `includes chores asked of you and leaves chores you asked for to the other member`() = runTest {
        fun item(choreId: String, declined: Boolean = false) = ChoreRequestItem(
            id = "request-$choreId",
            choreId = choreId,
            choreName = choreId,
            category = ChoreCategory.OTHER,
            otherMemberId = "member-2",
            otherMemberName = "Alex",
            note = null,
            createdAt = fixedInstant,
            declined = declined,
        )
        every { statisticsCalculator.buildStaleness(any(), any(), any(), any(), expectedToday) } returns listOf(
            staleness("asked-of-me", ChoreStatus.OK),
            staleness("asked-of-alex", ChoreStatus.NEEDS_ATTENTION),
            staleness("alex-declined", ChoreStatus.NEEDS_ATTENTION),
            staleness("overdue", ChoreStatus.NEEDS_ATTENTION),
        )
        every { observeChoreRequestsUseCase("household-1") } returns flowOf(
            ChoreRequests(
                askedOfMe = listOf(item("asked-of-me")),
                waitingOn = listOf(item("asked-of-alex"), item("alex-declined", declined = true)),
            ),
        )

        val result = useCase()

        assertThat(result.map(ChoreStaleness::choreId)).containsExactly("asked-of-me", "alex-declined", "overdue")
    }
}
