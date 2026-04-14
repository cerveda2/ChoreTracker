package cz.dcervenka.choretracker.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class UpdateChoreFrequencyUseCaseTest {

    @MockK
    lateinit var choreRepository: ChoreRepository

    private lateinit var useCase: UpdateChoreFrequencyUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { choreRepository.updateChoreFrequencyDays(any(), any()) } returns AppResult.Success(Unit)
        useCase = UpdateChoreFrequencyUseCase(choreRepository)
    }

    @Test
    fun `delegates positive frequency to repository`() = runTest {
        useCase("chore-1", 7)

        coVerify { choreRepository.updateChoreFrequencyDays("chore-1", 7) }
    }

    @Test
    fun `delegates null frequency to repository`() = runTest {
        useCase("chore-1", null)

        coVerify { choreRepository.updateChoreFrequencyDays("chore-1", null) }
    }

    @Test
    fun `returns error for zero frequency`() = runTest {
        val result = useCase("chore-1", 0)

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        coVerify(exactly = 0) { choreRepository.updateChoreFrequencyDays(any(), any()) }
    }

    @Test
    fun `returns error for negative frequency`() = runTest {
        val result = useCase("chore-1", -3)

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        coVerify(exactly = 0) { choreRepository.updateChoreFrequencyDays(any(), any()) }
    }
}
