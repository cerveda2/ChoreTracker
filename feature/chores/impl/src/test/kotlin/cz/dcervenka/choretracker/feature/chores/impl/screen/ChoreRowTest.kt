package cz.dcervenka.choretracker.feature.chores.impl.screen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChoreRowTest {

    @Test
    fun `freshnessFraction is null without a frequency`() {
        assertThat(freshnessFraction(daysSince = 10, frequencyDays = null)).isNull()
    }

    @Test
    fun `freshnessFraction is null without a last completion`() {
        assertThat(freshnessFraction(daysSince = null, frequencyDays = 7)).isNull()
    }

    @Test
    fun `freshnessFraction is null for a non-positive frequency`() {
        assertThat(freshnessFraction(daysSince = 5, frequencyDays = 0)).isNull()
    }

    @Test
    fun `freshnessFraction divides days since by the frequency`() {
        assertThat(freshnessFraction(daysSince = 3, frequencyDays = 6)).isEqualTo(0.5f)
        assertThat(freshnessFraction(daysSince = 6, frequencyDays = 6)).isEqualTo(1f)
        assertThat(freshnessFraction(daysSince = 12, frequencyDays = 6)).isEqualTo(2f)
    }
}
