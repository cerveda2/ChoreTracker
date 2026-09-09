package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalMemberPalette
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreListRow
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.MemberAvatar
import cz.dcervenka.choretracker.core.design.components.SectionHeader
import cz.dcervenka.choretracker.core.formatters.formatInstantForLocale
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion

@Composable
internal fun RecentActivitySection(
    completions: List<RecentCompletion>,
    hasMore: Boolean,
    memberIndexById: Map<String, Int>,
    onSeeAll: () -> Unit,
    onOpenCompletion: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val palette = LocalMemberPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_recent_activity),
            trailing = if (hasMore) {
                {
                    TextButton(onClick = onSeeAll) {
                        Text(text = stringResource(R.string.dashboard_see_all))
                    }
                }
            } else {
                null
            },
        )
        if (completions.isEmpty()) {
            ListGroup {
                EmptyState(
                    title = stringResource(R.string.dashboard_recent_completions_empty_title),
                    message = stringResource(R.string.dashboard_recent_completions_empty_message),
                    modifier = Modifier.padding(spacing.medium),
                )
            }
        } else {
            ListGroup {
                completions.forEachIndexed { index, completion ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.medium))
                    // Resolve against the same participant participantNames.firstOrNull() (below)
                    // came from - the leading id in participantMemberIds may belong to a member
                    // no longer in the household (memberIndexById only covers current members),
                    // in which case it's absent from participantNames too and this falls through
                    // to whichever id comes next, keeping the avatar's color and initial in sync.
                    val avatarIndex = completion.participantMemberIds
                        .firstOrNull { it in memberIndexById }
                        ?.let { memberIndexById[it] }
                        ?: 0
                    ChoreListRow(
                        leading = {
                            MemberAvatar(
                                initial = completion.participantNames.firstOrNull()?.take(1).orEmpty(),
                                color = palette.color(avatarIndex),
                                size = 36.dp,
                            )
                        },
                        title = stringResource(
                            R.string.dashboard_activity_row,
                            completion.participantNames.firstOrNull().orEmpty(),
                            completion.choreName,
                        ),
                        subtitle = activitySubtitle(completion),
                        onClick = { onOpenCompletion(completion.completionId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun activitySubtitle(completion: RecentCompletion): String {
    val dateLabel = formatInstantForLocale(completion.completedAt, "EEEMMMd")
    val note = completion.note?.takeIf(String::isNotBlank) ?: return dateLabel
    return "$dateLabel · " + stringResource(R.string.dashboard_activity_note_quoted, note)
}
