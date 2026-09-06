package cz.dcervenka.choretracker.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import kotlin.time.Instant

private val stringListSaver = Saver<SnapshotStateList<String>, List<String>>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

@Composable
fun rememberSaveableStringList(): SnapshotStateList<String> =
    rememberSaveable(saver = stringListSaver) { mutableStateListOf() }

// Saver's saved type must be non-null, so a present/absent Instant is encoded as a 1- or
// 0-element list of epoch millis rather than a nullable Long.
private val instantSaver = Saver<Instant?, List<Long>>(
    save = { instant -> instant?.let { listOf(it.toEpochMilliseconds()) } ?: emptyList() },
    restore = { millis -> millis.firstOrNull()?.let(Instant::fromEpochMilliseconds) },
)

@Composable
fun rememberSaveableInstant(): MutableState<Instant?> =
    rememberSaveable(stateSaver = instantSaver) { mutableStateOf(null) }
