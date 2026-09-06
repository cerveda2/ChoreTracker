package cz.dcervenka.choretracker.feature.settings.impl.navigation

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.feature.settings.impl.screen.AccountSettingsScreen
import cz.dcervenka.choretracker.feature.settings.impl.screen.AppLanguage
import cz.dcervenka.choretracker.feature.settings.impl.screen.ChoresSettingsScreen
import cz.dcervenka.choretracker.feature.settings.impl.screen.HouseholdSettingsScreen
import cz.dcervenka.choretracker.feature.settings.impl.screen.LanguageBottomSheetContent
import cz.dcervenka.choretracker.feature.settings.impl.screen.MembersSettingsScreen
import cz.dcervenka.choretracker.feature.settings.impl.screen.NotificationSettingsScreen
import cz.dcervenka.choretracker.feature.settings.impl.screen.SettingsScreen
import cz.dcervenka.choretracker.feature.settings.impl.viewmodel.NotificationSettingsViewModel
import cz.dcervenka.choretracker.feature.settings.impl.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.settingsScreen(
    navController: NavHostController,
) {
    composable(route = SettingsDestination.route) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        var showLanguageSheet by rememberSaveable { mutableStateOf(false) }
        val languageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        SettingsScreen(
            uiState = uiState.value,
            onOpenHousehold = { navController.navigate(HouseholdSettingsDestination.route) },
            onOpenMembers = { navController.navigate(MembersSettingsDestination.route) },
            onOpenChores = { navController.navigate(ChoresSettingsDestination.route) },
            onOpenAccount = { navController.navigate(AccountSettingsDestination.route) },
            onOpenLanguage = { showLanguageSheet = true },
            onOpenNotifications = { navController.navigate(NotificationSettingsDestination.route) },
        )

        if (showLanguageSheet) {
            val currentTag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java)
                    .applicationLocales
                    .takeIf { !it.isEmpty }
                    ?.get(0)
                    ?.toLanguageTag()
                    ?: ""
            } else {
                ""
            }

            ModalBottomSheet(
                onDismissRequest = { showLanguageSheet = false },
                sheetState = languageSheetState,
            ) {
                LanguageBottomSheetContent(
                    currentTag = currentTag,
                    onLanguageSelected = { language ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val locales = if (language == AppLanguage.System) {
                                LocaleList.getEmptyLocaleList()
                            } else {
                                LocaleList.forLanguageTags(language.tag)
                            }
                            context.getSystemService(LocaleManager::class.java).applicationLocales = locales
                        }
                        showLanguageSheet = false
                    },
                )
            }
        }
    }

    composable(route = HouseholdSettingsDestination.route) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        HouseholdSettingsScreen(
            uiState = uiState.value,
            events = viewModel.events,
            onBack = { navController.popBackStack() },
            onIntent = viewModel::dispatch,
        )
    }

    composable(route = MembersSettingsDestination.route) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        MembersSettingsScreen(
            uiState = uiState.value,
            events = viewModel.events,
            onBack = { navController.popBackStack() },
            onIntent = viewModel::dispatch,
        )
    }

    composable(route = ChoresSettingsDestination.route) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        ChoresSettingsScreen(
            uiState = uiState.value,
            events = viewModel.events,
            onBack = { navController.popBackStack() },
            onIntent = viewModel::dispatch,
        )
    }

    composable(route = AccountSettingsDestination.route) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        AccountSettingsScreen(
            uiState = uiState.value,
            events = viewModel.events,
            onBack = { navController.popBackStack() },
            onIntent = viewModel::dispatch,
        )
    }

    composable(route = NotificationSettingsDestination.route) {
        val viewModel: NotificationSettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        NotificationSettingsScreen(
            uiState = uiState.value,
            onBack = { navController.popBackStack() },
            onIntent = viewModel::dispatch,
        )
    }
}
