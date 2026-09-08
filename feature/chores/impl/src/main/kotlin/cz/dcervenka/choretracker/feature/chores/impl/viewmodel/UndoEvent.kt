package cz.dcervenka.choretracker.feature.chores.impl.viewmodel

data class UndoEvent(
    val completionId: String,
    val choreName: String,
)
