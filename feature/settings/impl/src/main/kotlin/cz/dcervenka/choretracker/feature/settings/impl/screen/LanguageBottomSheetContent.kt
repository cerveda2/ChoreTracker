package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R

@Composable
fun LanguageBottomSheetContent(
    currentTag: String,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    val spacing = LocalSpacing.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large, vertical = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text(
            text = stringResource(R.string.settings_language_title),
            style = MaterialTheme.typography.titleMedium,
        )
        AppLanguage.entries.forEach { language ->
            LanguageRow(
                language = language,
                selected = language.tag == currentTag,
                onSelect = { onLanguageSelected(language) },
            )
        }
    }
}

@Composable
private fun LanguageRow(
    language: AppLanguage,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val spacing = LocalSpacing.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
        )
        Text(
            text = language.displayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = spacing.small),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LanguageBottomSheetContentPreview() {
    ChoreTrackerTheme {
        LanguageBottomSheetContent(
            currentTag = "en",
            onLanguageSelected = {},
        )
    }
}
