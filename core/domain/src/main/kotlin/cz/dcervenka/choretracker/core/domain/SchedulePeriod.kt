package cz.dcervenka.choretracker.core.domain

import cz.dcervenka.choretracker.core.model.chore.ChoreSchedule
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

// The longest gap between two scheduled days, a month day (e.g. 31 July to 31 August).
private const val MAX_SCHEDULE_GAP_DAYS = 31

// start: the scheduled day a completion counts for. due: when the chore is due next.
internal data class SchedulePeriod(val start: LocalDate, val due: LocalDate)

// For a calendar schedule, a completion counts for the scheduled day nearest to it, the upcoming one on a tie,
// so doing a chore a little early or late still covers that day.
internal fun ChoreSchedule.periodFor(completed: LocalDate): SchedulePeriod {
    if (this is ChoreSchedule.EveryDays) return SchedulePeriod(completed, completed.plus(days, DateTimeUnit.DAY))
    val previous = scheduledDays(completed, step = -1).first()
    val next = scheduledDays(completed.plus(1, DateTimeUnit.DAY), step = 1).first()
    val start = if (previous.daysUntil(completed) < completed.daysUntil(next)) previous else next
    return SchedulePeriod(start, scheduledDays(start.plus(1, DateTimeUnit.DAY), step = 1).first())
}

private fun ChoreSchedule.scheduledDays(from: LocalDate, step: Int): Sequence<LocalDate> =
    generateSequence(from) { it.plus(step, DateTimeUnit.DAY) }
        .take(MAX_SCHEDULE_GAP_DAYS + 1)
        .filter { isScheduledOn(it) }

private fun ChoreSchedule.isScheduledOn(date: LocalDate): Boolean = when (this) {
    is ChoreSchedule.EveryDays -> true
    is ChoreSchedule.Weekdays -> date.dayOfWeek in days
    // A day the month doesn't have falls on its last day.
    is ChoreSchedule.MonthDay -> date.day == day || (date.day < day && date.plus(1, DateTimeUnit.DAY).day == 1)
}
