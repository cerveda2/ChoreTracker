package cz.dcervenka.choretracker.feature.settings.impl.screen

import android.content.Context
import android.content.Intent
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

internal fun shareInviteCode(context: Context, message: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
