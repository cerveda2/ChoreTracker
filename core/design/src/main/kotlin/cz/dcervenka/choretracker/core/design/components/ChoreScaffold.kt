package cz.dcervenka.choretracker.core.design.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
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

@Composable
fun TopLevelBottomBarSpacer() {
    Spacer(modifier = Modifier.height(TopLevelBottomBarHeight))
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
