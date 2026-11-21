package cz.dcervenka.choretracker.core.design.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.model.chore.ChoreSchedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private const val DAYS_IN_WEEK = 7

@Composable
fun choreScheduleLabel(schedule: ChoreSchedule): String = when (schedule) {
    is ChoreSchedule.EveryDays -> pluralStringResource(R.plurals.log_sheet_meta_frequency, schedule.days, schedule.days)
    is ChoreSchedule.Weekdays -> {
        val locale = LocalConfiguration.current.locales[0]
        val days = weekdaysInOrder(locale).filter { it in schedule.days }.joinToString(", ") { it.shortName(locale) }
        stringResource(R.string.schedule_weekdays, days)
    }
    is ChoreSchedule.MonthDay -> stringResource(R.string.schedule_month_day, schedule.day)
}

// Starting on the region's first day of the week, e.g. Monday in Czechia and Sunday in the US. A language picked in
// the app has no region ("en"), so the device's region decides then.
fun weekdaysInOrder(locale: Locale): List<DayOfWeek> {
    val regional = if (locale.country.isEmpty()) Resources.getSystem().configuration.locales[0] else locale
    val first = WeekFields.of(regional).firstDayOfWeek.value
    return DayOfWeek.entries.sortedBy { (it.isoDayNumber - first + DAYS_IN_WEEK) % DAYS_IN_WEEK }
}

fun DayOfWeek.shortName(locale: Locale): String =
    java.time.DayOfWeek.of(isoDayNumber).getDisplayName(TextStyle.SHORT, locale)
