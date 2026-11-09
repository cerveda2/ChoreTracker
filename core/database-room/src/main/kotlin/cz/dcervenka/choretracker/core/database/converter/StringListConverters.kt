package cz.dcervenka.choretracker.core.database.converter

import androidx.room.TypeConverter

// Applied per field, not database-wide, so List<String> query parameters keep expanding for IN (...).
class StringListConverters {
    @TypeConverter
    fun listToString(value: List<String>): String = value.joinToString(SEPARATOR)

    @TypeConverter
    fun stringToList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    private companion object {
        // Member ids are UUIDs or Firebase uids, neither of which contains a comma.
        const val SEPARATOR = ","
    }
}
