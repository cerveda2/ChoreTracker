package cz.dcervenka.choretracker.core.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = LocalSpacing.current

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
                content = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    content()
                },
            )
        }
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
                content()
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionCardPreview() {
    ChoreTrackerTheme {
        SectionCard(title = "Members") {
            Text("Dana • owner")
            Text("Alex • member")
        }
    }
}
