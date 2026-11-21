package cz.dcervenka.choretracker.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.model.chore.ChoreSchedule
import cz.dcervenka.choretracker.core.model.error.AppError
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import org.junit.Before
import org.junit.Test

class UpdateChoreScheduleUseCaseTest {

    @MockK
    lateinit var choreRepository: ChoreRepository

    private lateinit var useCase: UpdateChoreScheduleUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { choreRepository.updateChoreSchedule(any(), any()) } returns AppResult.Success(Unit)
        useCase = UpdateChoreScheduleUseCase(choreRepository)
    }

    @Test
    fun `delegates a valid schedule of each kind to the repository`() = runTest {
        val schedules = listOf(
            ChoreSchedule.EveryDays(7),
            ChoreSchedule.Weekdays(setOf(DayOfWeek.TUESDAY)),
            ChoreSchedule.MonthDay(31),
        )

        schedules.forEach { useCase("chore-1", it) }

        schedules.forEach { coVerify { choreRepository.updateChoreSchedule("chore-1", it) } }
    }

    @Test
    fun `delegates no schedule to the repository`() = runTest {
        useCase("chore-1", null)

        coVerify { choreRepository.updateChoreSchedule("chore-1", null) }
    }

    @Test
    fun `rejects an invalid schedule of each kind`() = runTest {
        val invalid = listOf(
            ChoreSchedule.EveryDays(0),
            ChoreSchedule.EveryDays(-3),
            ChoreSchedule.Weekdays(emptySet()),
            ChoreSchedule.MonthDay(0),
            ChoreSchedule.MonthDay(32),
        )

        invalid.forEach { assertThat(useCase("chore-1", it)).isEqualTo(AppResult.Error(AppError.INVALID_FREQUENCY)) }
        coVerify(exactly = 0) { choreRepository.updateChoreSchedule(any(), any()) }
    }
}
