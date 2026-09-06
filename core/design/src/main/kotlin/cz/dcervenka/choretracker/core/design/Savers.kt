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

// The SaveableStateRegistry only accepts concrete Bundle-friendly types - Iterable.toList()/
// listOf()/emptyList() can return the singleton kotlin.collections.EmptyList, which it rejects
// at runtime ("[] cannot be saved"). ArrayList(...) always allocates a real, saveable instance,
// even when empty.
private val stringListSaver = Saver<SnapshotStateList<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it.toMutableStateList() },
)

@Composable
fun rememberSaveableStringList(): SnapshotStateList<String> =
    rememberSaveable(saver = stringListSaver) { mutableStateListOf() }

// Saver's saved type must be non-null, so a present/absent Instant is encoded as a 1- or
// 0-element ArrayList of epoch millis rather than a nullable Long (see stringListSaver above for
// why it must be ArrayList, not emptyList()/listOf()).
private val instantSaver = Saver<Instant?, ArrayList<Long>>(
    save = { instant -> instant?.let { arrayListOf(it.toEpochMilliseconds()) } ?: ArrayList() },
    restore = { millis -> millis.firstOrNull()?.let(Instant::fromEpochMilliseconds) },
)

@Composable
fun rememberSaveableInstant(): MutableState<Instant?> =
    rememberSaveable(stateSaver = instantSaver) { mutableStateOf(null) }
