package cz.dcervenka.choretracker.feature.onboarding.impl.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiIntent
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiState

@Composable
fun QrScanScreen(
    uiState: OnboardingUiState,
    onIntent: (OnboardingUiIntent) -> Unit,
    onEnterManually: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        QrCodeScanner(
            onCodeScanned = { code ->
                onIntent(OnboardingUiIntent.InviteCodeChanged(code))
                onIntent(OnboardingUiIntent.JoinHousehold)
            },
            enabled = !uiState.isWorking,
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(spacing.medium),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Color.White,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.isWorking) {
                CircularProgressIndicator(color = Color.White)
            }
            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onEnterManually) {
                Text(
                    text = stringResource(R.string.onboarding_enter_code_manually),
                    color = Color.White,
                )
            }
        }
    }
}
