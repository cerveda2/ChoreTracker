package cz.dcervenka.choretracker.core.domain

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.chore.ChoreSchedule
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.Instant

// 2026-03-24 is a Tuesday.
class SchedulePeriodTest {

    private val tuesdays = ChoreSchedule.Weekdays(setOf(DayOfWeek.TUESDAY))

    @Test
    fun `an interval counts from the completion day`() {
        assertThat(ChoreSchedule.EveryDays(3).periodFor(date(26)))
            .isEqualTo(SchedulePeriod(start = date(26), due = date(29)))
    }

    @Test
    fun `a weekday completion on time, a day early or a day late counts for that day`() {
        val period = SchedulePeriod(start = date(24), due = date(31))

        assertThat(tuesdays.periodFor(date(24))).isEqualTo(period)
        assertThat(tuesdays.periodFor(date(23))).isEqualTo(period)
        assertThat(tuesdays.periodFor(date(25))).isEqualTo(period)
    }

    @Test
    fun `a completion halfway between two scheduled days counts for the upcoming one`() {
        val mondaysAndWednesdays = ChoreSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))

        assertThat(mondaysAndWednesdays.periodFor(date(24)))
            .isEqualTo(SchedulePeriod(start = date(25), due = date(30)))
    }

    @Test
    fun `a month day the month doesn't have falls on its last day`() {
        val thirtyFirst = ChoreSchedule.MonthDay(31)

        assertThat(thirtyFirst.periodFor(LocalDate(2026, 2, 28)))
            .isEqualTo(SchedulePeriod(start = LocalDate(2026, 2, 28), due = LocalDate(2026, 3, 31)))
        assertThat(thirtyFirst.periodFor(LocalDate(2026, 3, 31)))
            .isEqualTo(SchedulePeriod(start = LocalDate(2026, 3, 31), due = LocalDate(2026, 4, 30)))
    }

    @Test
    fun `a month day done days early counts for that month`() {
        assertThat(ChoreSchedule.MonthDay(1).periodFor(LocalDate(2026, 1, 28)))
            .isEqualTo(SchedulePeriod(start = LocalDate(2026, 2, 1), due = LocalDate(2026, 3, 1)))
    }

    @Test
    fun `a weekly chore done early is due again the following week`() {
        val bins = chore(scheduleWeekdays = setOf(DayOfWeek.TUESDAY), frequencyDays = 7)
        val doneMonday = listOf(completion("2026-03-23T19:00:00Z"))

        val sunday = staleness(bins, doneMonday, today = date(29))
        val tuesday = staleness(bins, doneMonday, today = date(31))

        assertThat(sunday.schedule).isEqualTo(tuesdays)
        assertThat(sunday.dueInDays).isEqualTo(2)
        assertThat(sunday.periodDays).isEqualTo(7)
        assertThat(sunday.status).isEqualTo(ChoreStatus.OK)
        assertThat(tuesday.status).isEqualTo(ChoreStatus.NEEDS_ATTENTION)
    }

    @Test
    fun `an older version changing the interval overrides the calendar schedule`() {
        val edited = chore(scheduleWeekdays = setOf(DayOfWeek.TUESDAY), frequencyDays = 3)

        assertThat(staleness(edited, emptyList(), today = date(29)).schedule).isEqualTo(ChoreSchedule.EveryDays(3))
    }

    private fun date(dayOfMarch: Int) = LocalDate(2026, 3, dayOfMarch)

    private fun staleness(chore: Chore, completions: List<ChoreCompletion>, today: LocalDate) =
        HouseholdStatisticsCalculator().buildStaleness(
            chores = listOf(chore),
            completions = completions,
            members = emptyList(),
            timeZone = TimeZone.UTC,
            today = today,
        ).single()

    private fun chore(scheduleWeekdays: Set<DayOfWeek>, frequencyDays: Int) = Chore(
        id = "chore-bins",
        householdId = "household-1",
        name = "Bins",
        isActive = true,
        createdAt = Instant.parse("2026-01-01T09:00:00Z"),
        frequencyDays = frequencyDays,
        scheduleWeekdays = scheduleWeekdays,
    )

    private fun completion(createdAt: String) = ChoreCompletion(
        id = "c1",
        householdId = "household-1",
        choreId = "chore-bins",
        createdAt = Instant.parse(createdAt),
        createdByUserId = "user-alice",
        note = null,
        participantMemberIds = emptyList(),
    )
}
