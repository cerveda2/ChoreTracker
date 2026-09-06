package cz.dcervenka.choretracker.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class UpdateCompletionUseCaseTest {

    @MockK
    lateinit var choreCompletionRepository: ChoreCompletionRepository

    private lateinit var useCase: UpdateCompletionUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery {
            choreCompletionRepository.updateCompletion(any(), any(), any())
        } returns AppResult.Success(Unit)
        useCase = UpdateCompletionUseCase(choreCompletionRepository)
    }

    @Test
    fun `delegates all parameters to repository`() = runTest {
        useCase(
            completionId = "completion-1",
            note = "Updated note",
            participantMemberIds = listOf("member-1", "member-2"),
        )

        coVerify {
            choreCompletionRepository.updateCompletion(
                completionId = "completion-1",
                note = "Updated note",
                participantMemberIds = listOf("member-1", "member-2"),
            )
        }
    }

    @Test
    fun `returns error result`() = runTest {
        coEvery {
            choreCompletionRepository.updateCompletion(any(), any(), any())
        } returns AppResult.Error("Not authenticated")

        val result = useCase("completion-1", null, listOf("member-1"))

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
    }

    @Test
    fun `rejects a completion with no participants without calling the repository`() = runTest {
        val result = useCase("completion-1", null, emptyList())

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        coVerify(exactly = 0) { choreCompletionRepository.updateCompletion(any(), any(), any()) }
    }
}
