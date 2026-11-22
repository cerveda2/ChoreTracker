package cz.dcervenka.choretracker.core.model.sync

import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion

data class PersonalSnapshot(
    val chores: List<Chore>,
    val completions: List<ChoreCompletion>,
)
