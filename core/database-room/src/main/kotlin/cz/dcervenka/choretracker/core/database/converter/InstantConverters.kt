package cz.dcervenka.choretracker.core.database.converter

import androidx.room.TypeConverter
import kotlin.time.Instant

class InstantConverters {
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)
}
