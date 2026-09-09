package cz.dcervenka.choretracker.core.design.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme

val TopLevelBottomBarHeight = 80.dp

// M3's Scaffold does not add extra content padding to clear a floating action button - its
// content padding only ever reflects the bottomBar's height, so a FAB floating above that bar
// visually overlaps scrollable content unless the content's own bottom padding also reserves
// space for it. 56 dp is ExtendedFloatingActionButton's height; 16 dp is the margin Scaffold
// places between the FAB and whatever sits below it (here, TopLevelBottomBarHeight's spacer).
val ExtendedFabReservedHeight = 72.dp

@Composable
fun ChoreScaffold(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Scaffold(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { focusManager.clearFocus() }
        },
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        contentWindowInsets = contentWindowInsets,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

// The real bottom NavigationBar (ChoreTrackerRoot) pads itself for the device's bottom
// navigation/gesture inset on top of its 80 dp content height, since the app-level Scaffold's
// own contentWindowInsets is zeroed and its innerPadding is discarded (each top-level screen
// reserves space for the bar itself, via this composable, instead). A flat 80 dp spacer here
// under-reserves by exactly that inset on any device with gesture navigation, which both crowds
// scrollable content's last items and - because a FAB is positioned relative to this same
// bottomBar slot - lets a screen's FAB sit low enough to overlap the real bar underneath it.
@Composable
fun TopLevelBottomBarSpacer() {
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Spacer(modifier = Modifier.height(TopLevelBottomBarHeight + navigationBarInset))
}

@Preview(showBackground = true)
@Composable
private fun ChoreScaffoldPreview() {
    ChoreTrackerTheme {
        ChoreScaffold {
            Text("Scaffold content")
        }
    }
}
