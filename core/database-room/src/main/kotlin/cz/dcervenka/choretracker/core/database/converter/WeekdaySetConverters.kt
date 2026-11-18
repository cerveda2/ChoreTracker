package cz.dcervenka.choretracker.core.database.converter

import androidx.room.TypeConverter
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

// A bitmask, bit 0 for Monday. Applied per field, like StringListConverters.
class WeekdaySetConverters {
    @TypeConverter
    fun setToMask(value: Set<DayOfWeek>): Int = value.fold(0) { mask, day -> mask or (1 shl (day.isoDayNumber - 1)) }

    @TypeConverter
    fun maskToSet(value: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filterTo(mutableSetOf()) { day -> value and (1 shl (day.isoDayNumber - 1)) != 0 }
}
