package cz.dcervenka.choretracker.core.design.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing

@Composable
fun LoadingState(
    message: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current

    Text(
        text = message,
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.large),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true)
@Composable
private fun LoadingStatePreview() {
    ChoreTrackerTheme {
        LoadingState(message = "Loading preview…")
    }
}
