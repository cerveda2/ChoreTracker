package cz.dcervenka.choretracker.core.design.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cz.dcervenka.choretracker.core.design.LocalSpacing

@Composable
fun ChoreTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    val spacing = LocalSpacing.current

    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier
                    .tabIndicatorOffset(selectedTabIndex, matchContentSize = false)
                    .padding(horizontal = spacing.medium),
                height = spacing.xSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        divider = {},
        tabs = tabs,
    )
}
