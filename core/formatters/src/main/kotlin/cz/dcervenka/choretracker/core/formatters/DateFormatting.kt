package cz.dcervenka.choretracker.core.formatters

import android.text.format.DateFormat
import kotlinx.datetime.LocalDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Instant

fun formatInstantForLocale(
    instant: Instant,
    skeleton: String,
    locale: Locale = Locale.getDefault(),
): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return SimpleDateFormat(pattern, locale).apply {
        timeZone = TimeZone.getDefault()
    }.format(Date(instant.toEpochMilliseconds()))
}

fun formatLocalDateForLocale(
    date: LocalDate,
    skeleton: String,
    locale: Locale = Locale.getDefault(),
): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return SimpleDateFormat(pattern, locale).format(
        Date(
            java.util.GregorianCalendar(date.year, date.month.ordinal, date.day)
                .timeInMillis,
        ),
    )
}

fun formatMonthLabelForLocale(
    rawLabel: String,
    locale: Locale = Locale.getDefault(),
): String {
    val year = rawLabel.substringBefore('-').toIntOrNull()
    val month = rawLabel.substringAfter('-', "").toIntOrNull()
    if (year == null || month == null) {
        return rawLabel
    }

    val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
    return SimpleDateFormat(pattern, locale).format(
        Date(
            java.util.GregorianCalendar(year, month - 1, 1).timeInMillis,
        ),
    )
}
