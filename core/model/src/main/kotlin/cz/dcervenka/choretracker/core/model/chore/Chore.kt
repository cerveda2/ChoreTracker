package cz.dcervenka.choretracker.core.model.chore

import kotlinx.datetime.DayOfWeek
import kotlin.time.Instant

data class Chore(
    val id: String,
    val householdId: String,
    val name: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val deletedAt: Instant? = null,
    val frequencyDays: Int? = null,
    val category: ChoreCategory = ChoreCategory.OTHER,
    val createdByUserId: String? = null,
    // Members (HouseholdMember.id) who take turns; empty when the chore doesn't rotate.
    val rotationMemberIds: List<String> = emptyList(),
    // Only completions from this moment on count toward whose turn it is.
    val rotationStartedAt: Instant? = null,
    // Calendar schedule fields, read through schedule.
    val scheduleWeekdays: Set<DayOfWeek> = emptySet(),
    val scheduleMonthDay: Int? = null,
) {
    val schedule: ChoreSchedule?
        get() {
            val calendar = when {
                scheduleWeekdays.isNotEmpty() -> ChoreSchedule.Weekdays(scheduleWeekdays)
                scheduleMonthDay != null -> ChoreSchedule.MonthDay(scheduleMonthDay)
                else -> null
            }?.takeIf { it.isValid }
            return when {
                // Builds up to 0.3.0 only edit frequencyDays and leave the calendar fields behind, so a
                // frequencyDays that no longer matches the calendar schedule's means they've changed it since.
                calendar != null && frequencyDays == calendar.frequencyDays -> calendar
                frequencyDays != null -> ChoreSchedule.EveryDays(frequencyDays)
                else -> null
            }
        }
}
