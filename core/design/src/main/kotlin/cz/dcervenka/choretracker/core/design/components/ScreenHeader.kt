package cz.dcervenka.choretracker.core.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cz.dcervenka.choretracker.core.design.LocalSpacing

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
