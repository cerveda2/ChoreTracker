package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.SectionCard

@Composable
fun LanguageSettingsScreen(
    currentTag: String,
    onBack: () -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    val spacing = LocalSpacing.current

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.settings_language_title),
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(
                start = spacing.large,
                top = innerPadding.calculateTopPadding() + spacing.medium,
                end = spacing.large,
                bottom = innerPadding.calculateBottomPadding() + spacing.large,
            ),
        ) {
            item {
                SectionCard(title = stringResource(R.string.settings_language_title)) {
                    Column {
                        AppLanguage.entries.forEach { language ->
                            LanguageRow(
                                language = language,
                                selected = language.tag == currentTag,
                                onSelect = { onLanguageSelected(language) },
                            )
                        }
                    }
                }
            }
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
private fun LanguageSettingsScreenPreview() {
    ChoreTrackerTheme {
        LanguageSettingsScreen(
            currentTag = "en",
            onBack = {},
            onLanguageSelected = {},
        )
    }
}
