package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import cz.dcervenka.choretracker.core.design.R

@Composable
internal fun ChoreFrequencyDialog(
    choreId: String,
    initialFrequencyDays: Int?,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
    onClear: () -> Unit,
) {
    var frequencyInput by remember(choreId) {
        mutableStateOf(initialFrequencyDays?.toString().orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_chore_set_frequency_title)) },
        text = {
            OutlinedTextField(
                value = frequencyInput,
                onValueChange = { frequencyInput = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.settings_chore_set_frequency_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(frequencyInput.toIntOrNull()?.takeIf { it > 0 })
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.settings_chore_clear_frequency))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}
