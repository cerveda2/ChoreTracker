package cz.dcervenka.choretracker.core.model.settings

data class ReminderSettings(
    val enabled: Boolean = true,
    val hour: Int = 9,
    val minute: Int = 0,
)
