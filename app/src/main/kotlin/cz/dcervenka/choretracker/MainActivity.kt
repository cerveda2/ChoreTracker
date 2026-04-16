package cz.dcervenka.choretracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import cz.dcervenka.choretracker.navigation.RootDestination
import cz.dcervenka.choretracker.screen.ChoreTrackerRoot
import cz.dcervenka.choretracker.viewmodel.AppViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            appViewModel.rootDestination.value == RootDestination.Loading
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChoreTrackerRoot()
        }
    }
}
