package cz.dcervenka.choretracker.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.domain.HouseholdStatisticsCalculator
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
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
        every { statisticsCalculator.buildStaleness(any(), any(), any(), expectedToday) } returns emptyList()
        useCase = CheckStaleChoresUseCase(
            authRepository,
            refreshHouseholdUseCase,
            householdRepository,
            statsRepository,
            statisticsCalculator,
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
        coEvery { refreshHouseholdUseCase() } returns AppResult.Error("Network error")
        coEvery { statsRepository.getHouseholdStatsInput("household-1") } returns emptyStatsInput
        every { statisticsCalculator.buildStaleness(any(), any(), any(), expectedToday) } returns
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
        every { statisticsCalculator.buildStaleness(any(), any(), any(), expectedToday) } returns listOf(
            staleness("chore-1", ChoreStatus.NEEDS_ATTENTION),
            staleness("chore-2", ChoreStatus.SOON),
            staleness("chore-3", ChoreStatus.OK),
            staleness("chore-4", ChoreStatus.NEVER),
        )

        val result = useCase()

        assertThat(result.map(ChoreStaleness::choreId)).containsExactly("chore-1")
    }
}
