package cz.dcervenka.choretracker.core.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.model.stats.ChoreTurn

@Composable
fun choreTurnLabel(turn: ChoreTurn): String = if (turn.isCurrentUser) {
    stringResource(R.string.chore_turn_yours)
} else {
    stringResource(R.string.chore_turn_member, turn.memberName)
}

@Composable
fun ChoreTurnLine(turn: ChoreTurn, modifier: Modifier = Modifier) {
    val color = if (turn.isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.xSmall),
    ) {
        Icon(
            imageVector = Icons.Outlined.Autorenew,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(text = choreTurnLabel(turn), style = MaterialTheme.typography.labelSmall, color = color)
    }
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
