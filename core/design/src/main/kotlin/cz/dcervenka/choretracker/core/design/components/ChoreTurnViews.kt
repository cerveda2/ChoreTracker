package cz.dcervenka.choretracker.core.design.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.model.stats.ChoreTurn

@Composable
fun choreTurnLabel(turn: ChoreTurn): String = if (turn.isCurrentUser) {
    stringResource(R.string.chore_turn_yours)
} else {
    stringResource(R.string.chore_turn_member, turn.memberName)
}

@Composable
fun OutOfTurnDialog(turn: ChoreTurn, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chore_out_of_turn_title, turn.memberName)) },
        text = { Text(stringResource(R.string.chore_out_of_turn_message, turn.memberName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.chore_out_of_turn_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
