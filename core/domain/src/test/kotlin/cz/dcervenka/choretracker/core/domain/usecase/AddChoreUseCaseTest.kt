package cz.dcervenka.choretracker.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreSchedule
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import org.junit.Before
import org.junit.Test

class AddChoreUseCaseTest {

    @MockK
    lateinit var choreRepository: ChoreRepository

    private lateinit var useCase: AddChoreUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { choreRepository.addChore(any(), any(), any(), any()) } returns AppResult.Success("chore-1")
        useCase = AddChoreUseCase(choreRepository)
    }

    @Test
    fun `delegates to repository with no schedule by default`() = runTest {
        useCase("household-1", "Kitchen", ChoreCategory.COOKING)

        coVerify {
            choreRepository.addChore(
                householdId = "household-1",
                name = "Kitchen",
                category = ChoreCategory.COOKING,
                schedule = null,
            )
        }
    }

    @Test
    fun `delegates a valid schedule to repository`() = runTest {
        val schedule = ChoreSchedule.Weekdays(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY))

        useCase("household-1", "Bins", ChoreCategory.OTHER, schedule)

        coVerify {
            choreRepository.addChore(
                householdId = "household-1",
                name = "Bins",
                category = ChoreCategory.OTHER,
                schedule = schedule,
            )
        }
    }

    @Test
    fun `returns the new chore's id from the repository`() = runTest {
        val result = useCase("household-1", "Kitchen", ChoreCategory.COOKING)

        assertThat(result).isEqualTo(AppResult.Success("chore-1"))
    }

    @Test
    fun `returns error for an invalid schedule`() = runTest {
        val result = useCase("household-1", "Kitchen", ChoreCategory.COOKING, ChoreSchedule.EveryDays(0))

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        coVerify(exactly = 0) { choreRepository.addChore(any(), any(), any(), any()) }
    }
}
