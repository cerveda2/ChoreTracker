package cz.dcervenka.choretracker.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AddChoreUseCaseTest {

    @MockK
    lateinit var choreRepository: ChoreRepository

    private lateinit var useCase: AddChoreUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { choreRepository.addChore(any(), any(), any(), any()) } returns AppResult.Success(Unit)
        useCase = AddChoreUseCase(choreRepository)
    }

    @Test
    fun `delegates to repository with no frequency by default`() = runTest {
        useCase("household-1", "Kitchen", ChoreCategory.COOKING)

        coVerify {
            choreRepository.addChore(
                householdId = "household-1",
                name = "Kitchen",
                category = ChoreCategory.COOKING,
                frequencyDays = null,
            )
        }
    }

    @Test
    fun `delegates positive frequency to repository`() = runTest {
        useCase("household-1", "Kitchen", ChoreCategory.COOKING, 6)

        coVerify {
            choreRepository.addChore(
                householdId = "household-1",
                name = "Kitchen",
                category = ChoreCategory.COOKING,
                frequencyDays = 6,
            )
        }
    }

    @Test
    fun `returns error for zero frequency`() = runTest {
        val result = useCase("household-1", "Kitchen", ChoreCategory.COOKING, 0)

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        coVerify(exactly = 0) { choreRepository.addChore(any(), any(), any(), any()) }
    }

    @Test
    fun `returns error for negative frequency`() = runTest {
        val result = useCase("household-1", "Kitchen", ChoreCategory.COOKING, -3)

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        coVerify(exactly = 0) { choreRepository.addChore(any(), any(), any(), any()) }
    }
}
