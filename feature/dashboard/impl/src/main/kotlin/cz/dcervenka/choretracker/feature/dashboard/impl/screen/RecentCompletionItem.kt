package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontStyle
import cz.dcervenka.choretracker.core.formatters.formatInstantForLocale
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion

@Composable
internal fun RecentCompletionContent(
    completion: RecentCompletion,
    dateSkeleton: String = "yMMMd",
) {
    Text(
        text = completion.choreName,
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = formatInstantForLocale(completion.completedAt, dateSkeleton),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = completion.participantNames.joinToString(),
        style = MaterialTheme.typography.labelLarge,
    )
    completion.note?.takeIf(String::isNotBlank)?.let { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
