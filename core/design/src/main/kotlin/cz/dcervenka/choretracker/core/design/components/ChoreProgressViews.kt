package cz.dcervenka.choretracker.core.design.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DoneButton(
    filled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .then(
                if (filled) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                } else {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = contentDescription,
            tint = if (filled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun FreshnessBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(2.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(color, RoundedCornerShape(2.dp)),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChoreProgressViewsPreview() {
    ChoreTrackerTheme {
        Row {
            DoneButton(filled = true, onClick = {}, contentDescription = "Mark Vaření done")
            DoneButton(filled = false, onClick = {}, contentDescription = "Mark Úklid done")
        }
    }
}
