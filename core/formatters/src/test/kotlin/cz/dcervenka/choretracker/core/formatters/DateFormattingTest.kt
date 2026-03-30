package cz.dcervenka.choretracker.core.formatters

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class DateFormattingTest {

    private lateinit var originalDefaultLocale: Locale
    private lateinit var originalDefaultTimeZone: TimeZone

    @Before
    fun setUp() {
        originalDefaultLocale = Locale.getDefault()
        originalDefaultTimeZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalDefaultLocale)
        TimeZone.setDefault(originalDefaultTimeZone)
    }

    @Test
    fun `formatInstantForLocale reflects the active time zone`() {
        val instant = Instant.parse("2026-03-29T23:30:00Z")

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val utcDay = formatInstantForLocale(
            instant = instant,
            skeleton = "d",
            locale = Locale.US,
        )

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Prague"))
        val pragueDay = formatInstantForLocale(
            instant = instant,
            skeleton = "d",
            locale = Locale.US,
        )

        assertThat(utcDay).isEqualTo("29")
        assertThat(pragueDay).isEqualTo("30")
    }

    @Test
    fun `formatInstantForLocale adapts month language to locale`() {
        val instant = Instant.parse("2026-03-30T09:15:00Z")

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        val us = formatInstantForLocale(
            instant = instant,
            skeleton = "yMMMMd",
            locale = Locale.US,
        )
        val czech = formatInstantForLocale(
            instant = instant,
            skeleton = "yMMMMd",
            locale = Locale.forLanguageTag("cs-CZ"),
        )

        assertThat(us).contains("March")
        assertThat(czech.lowercase(Locale.ROOT)).contains("bře")
        assertThat(czech).contains("2026")
    }

    @Test
    fun `formatLocalDateForLocale returns localized month text`() {
        val formatted = formatLocalDateForLocale(
            date = LocalDate(2026, 3, 30),
            skeleton = "yMMMMd",
            locale = Locale.US,
        )

        assertThat(formatted).contains("March")
        assertThat(formatted).contains("2026")
    }

    @Test
    fun `formatMonthLabelForLocale expands storage label into localized month and year`() {
        val us = formatMonthLabelForLocale(
            rawLabel = "2026-03",
            locale = Locale.US,
        )
        val czech = formatMonthLabelForLocale(
            rawLabel = "2026-03",
            locale = Locale.forLanguageTag("cs-CZ"),
        )

        assertThat(us).contains("March")
        assertThat(us).contains("2026")
        assertThat(czech.lowercase(Locale.ROOT)).contains("bře")
        assertThat(czech).contains("2026")
    }
}
