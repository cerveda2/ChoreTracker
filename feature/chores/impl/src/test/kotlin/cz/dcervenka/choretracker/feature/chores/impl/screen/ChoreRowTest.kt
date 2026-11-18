package cz.dcervenka.choretracker.feature.chores.impl.screen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChoreRowTest {

    @Test
    fun `freshnessFraction is null without a schedule`() {
        assertThat(freshnessFraction(periodDays = null, dueInDays = null)).isNull()
    }

    @Test
    fun `freshnessFraction is null without a last completion`() {
        assertThat(freshnessFraction(periodDays = 7, dueInDays = null)).isNull()
    }

    @Test
    fun `freshnessFraction is null for a non-positive period`() {
        assertThat(freshnessFraction(periodDays = 0, dueInDays = -5)).isNull()
    }

    @Test
    fun `freshnessFraction is the share of the period that has passed`() {
        assertThat(freshnessFraction(periodDays = 6, dueInDays = 3)).isEqualTo(0.5f)
        assertThat(freshnessFraction(periodDays = 6, dueInDays = 0)).isEqualTo(1f)
        assertThat(freshnessFraction(periodDays = 6, dueInDays = -6)).isEqualTo(2f)
    }
}
