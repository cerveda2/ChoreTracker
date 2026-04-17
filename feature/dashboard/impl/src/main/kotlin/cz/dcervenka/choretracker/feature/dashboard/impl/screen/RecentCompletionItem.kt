package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.formatters.formatInstantForLocale
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion

@Composable
internal fun RecentCompletionRow(
    completion: RecentCompletion,
    onClick: () -> Unit,
    roundedBackground: Boolean = false,
    trailingText: String = formatInstantForLocale(completion.completedAt, "MMMd"),
    emphasizeAsActivity: Boolean = false,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(
                vertical = if (roundedBackground) spacing.xSmall else spacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emphasizeAsActivity) {
            val badgeLabel = completion.choreName.take(1).uppercase()
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.medium),
        ) {
            Text(
                text = completion.choreName,
                style = if (emphasizeAsActivity) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            )
            Text(
                text = completion.participantNames.joinToString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = trailingText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = spacing.medium),
        )
    }
}

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
