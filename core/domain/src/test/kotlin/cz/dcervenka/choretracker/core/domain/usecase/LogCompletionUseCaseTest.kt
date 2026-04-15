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
import kotlin.time.Instant

class LogCompletionUseCaseTest {

    @MockK
    lateinit var choreCompletionRepository: ChoreCompletionRepository

    private lateinit var useCase: LogCompletionUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery {
            choreCompletionRepository.logCompletion(any(), any(), any(), any(), any())
        } returns AppResult.Success("completion-id")
        useCase = LogCompletionUseCase(choreCompletionRepository)
    }

    @Test
    fun `delegates all parameters to repository`() = runTest {
        val completedAt = Instant.parse("2026-03-15T12:00:00Z")

        useCase(
            householdId = "household-1",
            choreId = "chore-1",
            participantMemberIds = listOf("member-1", "member-2"),
            note = "Done together",
            completedAt = completedAt,
        )

        coVerify {
            choreCompletionRepository.logCompletion(
                householdId = "household-1",
                choreId = "chore-1",
                participantMemberIds = listOf("member-1", "member-2"),
                note = "Done together",
                completedAt = completedAt,
            )
        }
    }

    @Test
    fun `passes null completedAt by default`() = runTest {
        useCase(
            householdId = "household-1",
            choreId = "chore-1",
            participantMemberIds = listOf("member-1"),
            note = null,
        )

        coVerify {
            choreCompletionRepository.logCompletion(
                householdId = "household-1",
                choreId = "chore-1",
                participantMemberIds = listOf("member-1"),
                note = null,
                completedAt = null,
            )
        }
    }

    @Test
    fun `returns completion id on success`() = runTest {
        coEvery {
            choreCompletionRepository.logCompletion(any(), any(), any(), any(), any())
        } returns AppResult.Success("completion-abc")

        val result = useCase("h", "c", listOf("m"), null)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).value).isEqualTo("completion-abc")
    }

    @Test
    fun `returns error result`() = runTest {
        coEvery {
            choreCompletionRepository.logCompletion(any(), any(), any(), any(), any())
        } returns AppResult.Error("Not authenticated")

        val result = useCase("h", "c", listOf("m"), null)

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
    }
}
