package cz.dcervenka.choretracker.core.model.chore

import kotlinx.datetime.DayOfWeek
import kotlin.math.roundToInt

private const val DAYS_IN_WEEK = 7
private const val MONTH_DAY_FREQUENCY_DAYS = 30
private const val MAX_MONTH_DAY = 31

sealed interface ChoreSchedule {
    data class EveryDays(val days: Int) : ChoreSchedule

    data class Weekdays(val days: Set<DayOfWeek>) : ChoreSchedule

    // A day the month doesn't have (e.g. the 31st in April) means its last day.
    data class MonthDay(val day: Int) : ChoreSchedule
}

// Stored in Chore.frequencyDays next to the schedule. For a calendar schedule it's the interval builds up to
// 0.3.0 show and compute staleness from, since they only know frequencyDays.
val ChoreSchedule.frequencyDays: Int
    get() = when (this) {
        is ChoreSchedule.EveryDays -> days
        is ChoreSchedule.Weekdays -> (DAYS_IN_WEEK.toDouble() / days.size).roundToInt()
        is ChoreSchedule.MonthDay -> MONTH_DAY_FREQUENCY_DAYS
    }

val ChoreSchedule.isValid: Boolean
    get() = when (this) {
        is ChoreSchedule.EveryDays -> days > 0
        is ChoreSchedule.Weekdays -> days.isNotEmpty()
        is ChoreSchedule.MonthDay -> day in 1..MAX_MONTH_DAY
    }
