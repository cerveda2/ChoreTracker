package cz.dcervenka.choretracker.core.design.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cz.dcervenka.choretracker.core.common.performance.NoOpPerformanceMonitor
import cz.dcervenka.choretracker.core.common.performance.PerformanceMonitor
import cz.dcervenka.choretracker.core.common.performance.PerformanceTraceName
import cz.dcervenka.choretracker.core.common.performance.ScreenLoadTrace

val LocalPerformanceMonitor = staticCompositionLocalOf<PerformanceMonitor> { NoOpPerformanceMonitor }

@Composable
fun TrackScreenLoad(name: PerformanceTraceName, isLoading: Boolean) {
    if (LocalInspectionMode.current) return
    val monitor = LocalPerformanceMonitor.current
    val trace = remember(name, monitor) { ScreenLoadTrace(monitor, name) }

    DisposableEffect(trace) {
        trace.start(isLoading)
        onDispose { trace.cancel() }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { trace.cancel() }

    LaunchedEffect(trace, isLoading) {
        if (!isLoading) {
            withFrameNanos { }
            trace.ready()
        }
    }
}
