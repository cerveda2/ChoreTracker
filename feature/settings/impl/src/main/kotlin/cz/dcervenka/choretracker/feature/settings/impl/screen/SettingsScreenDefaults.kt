package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import cz.dcervenka.choretracker.core.design.LocalSpacing

@Composable
internal fun detailContentPadding(innerPadding: PaddingValues): PaddingValues {
    val spacing = LocalSpacing.current
    return PaddingValues(
        start = spacing.large,
        top = innerPadding.calculateTopPadding() + spacing.large,
        end = spacing.large,
        bottom = innerPadding.calculateBottomPadding() + spacing.large,
    )
}
