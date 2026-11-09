package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreListRow
import cz.dcervenka.choretracker.core.design.components.CountBadge
import cz.dcervenka.choretracker.core.design.components.DoneButton
import cz.dcervenka.choretracker.core.design.components.IconCircle
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.SectionHeader
import cz.dcervenka.choretracker.core.design.toIcon
import cz.dcervenka.choretracker.core.model.chore.ChoreRequestItem

// Logging the chore is what answers a request, so Done here is the usual quick log.
@Composable
internal fun AskedOfYouSection(
    items: List<ChoreRequestItem>,
    onDone: (choreId: String) -> Unit,
    onDecline: (requestId: String) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_asked_of_you),
            trailing = { CountBadge(count = items.size) },
        )
        ListGroup {
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.medium))
                ChoreListRow(
                    leading = { IconCircle(icon = item.category.toIcon()) },
                    title = item.choreName,
                    subtitle = listOfNotNull(
                        stringResource(R.string.dashboard_request_from, item.otherMemberName),
                        item.note,
                    ).joinToString(" · "),
                    trailing = {
                        DoneButton(
                            filled = true,
                            onClick = { onDone(item.choreId) },
                            contentDescription = stringResource(R.string.common_mark_done, item.choreName),
                        )
                    },
                    belowSubtitle = {
                        TextButton(onClick = { onDecline(item.id) }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.dashboard_request_decline))
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun WaitingOnSection(items: List<ChoreRequestItem>, onCancel: (requestId: String) -> Unit) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        SectionHeader(title = stringResource(R.string.dashboard_waiting_on))
        ListGroup {
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.medium))
                ChoreListRow(
                    leading = { IconCircle(icon = item.category.toIcon()) },
                    title = item.choreName,
                    subtitle = if (item.declined) {
                        stringResource(R.string.dashboard_request_declined, item.otherMemberName)
                    } else {
                        listOfNotNull(stringResource(R.string.dashboard_request_to, item.otherMemberName), item.note)
                            .joinToString(" · ")
                    },
                    subtitleColor = if (item.declined) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    trailing = if (item.declined) {
                        null
                    } else {
                        {
                            IconButton(onClick = { onCancel(item.id) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(
                                        R.string.dashboard_request_cancel,
                                        item.choreName,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
