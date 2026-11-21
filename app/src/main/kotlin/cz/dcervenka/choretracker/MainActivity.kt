package cz.dcervenka.choretracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import cz.dcervenka.choretracker.core.common.performance.PerformanceMonitor
import cz.dcervenka.choretracker.core.design.performance.LocalPerformanceMonitor
import cz.dcervenka.choretracker.screen.ChoreTrackerRoot
import cz.dcervenka.choretracker.viewmodel.AppViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var performanceMonitor: PerformanceMonitor

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !appViewModel.isReady.value
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalPerformanceMonitor provides performanceMonitor) {
                ChoreTrackerRoot()
            }
        }
    }
}
