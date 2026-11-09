package cz.dcervenka.choretracker.feature.chores.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreChip
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.rememberSaveableStringList
import cz.dcervenka.choretracker.core.design.toIcon
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.household.HouseholdMember

// members are the ones who can be asked: still in the household, with an account, and not you.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AskSheet(
    members: List<HouseholdMember>,
    chores: List<Chore>,
    onDismiss: () -> Unit,
    onSend: (toMemberId: String, choreIds: List<String>, note: String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var toMemberId by rememberSaveable { mutableStateOf(members.singleOrNull()?.id) }
    val choreIds = rememberSaveableStringList()
    var note by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            val onlyMember = members.singleOrNull()
            Text(
                text = if (onlyMember != null) {
                    stringResource(R.string.chores_ask_title_member, onlyMember.displayName)
                } else {
                    stringResource(R.string.chores_ask_title)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (onlyMember == null) {
                Text(
                    text = stringResource(R.string.chores_ask_who),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    members.forEach { member ->
                        ChoreChip(
                            selected = member.id == toMemberId,
                            onClick = { toMemberId = member.id },
                            label = member.displayName,
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.chores_ask_what),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                chores.forEach { chore ->
                    ChoreChip(
                        selected = chore.id in choreIds,
                        onClick = { if (chore.id in choreIds) choreIds.remove(chore.id) else choreIds.add(chore.id) },
                        label = chore.name,
                        leadingIcon = { Icon(imageVector = chore.category.toIcon(), contentDescription = null) },
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.chores_ask_note_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.chores_ask_send),
                onClick = { toMemberId?.let { onSend(it, choreIds.toList(), note) } },
                enabled = toMemberId != null && choreIds.isNotEmpty(),
            )
        }
    }
}
