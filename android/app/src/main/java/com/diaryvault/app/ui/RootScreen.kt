package com.diaryvault.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.ui.auth.PhoneOTPLoginScreen
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.diary.DiaryNavHost
import com.diaryvault.app.ui.mirror.MirrorTabScreen
import com.diaryvault.app.ui.onboarding.OnboardingScreen
import com.diaryvault.app.ui.settings.SettingsScreen
import com.diaryvault.app.ui.theme.VaultBackground
import com.diaryvault.app.ui.theme.VaultColors

@Composable
fun RootScreen(viewModel: VaultViewModel) {
    val done by viewModel.didCompleteOnboarding.collectAsState()
    val signedIn by viewModel.accountSignedIn.collectAsState()

    AnimatedContent(
        targetState = when {
            !done -> "onboarding"
            !signedIn -> "auth"
            else -> "main"
        },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "root",
    ) { stage ->
        when (stage) {
            "onboarding" -> OnboardingScreen(viewModel)
            "auth" -> PhoneOTPLoginScreen(viewModel)
            else -> MainTabs(viewModel)
        }
    }
}

@Composable
private fun MainTabs(viewModel: VaultViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    VaultBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            // Child tabs own status-bar / top chrome. Zero content insets so nested Scaffolds
            // + FAB are not double-padded against MIUI 3-button nav (Redmi 9C overlap).
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // NavigationBarDefaults.windowInsets pads above system nav buttons.
                NavigationBar(containerColor = VaultColors.paper.copy(alpha = 0.94f)) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                        label = { Text(localizedString(R.string.tab_vault)) },
                        colors = navColors(),
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        label = { Text(localizedString(R.string.tab_mirror)) },
                        colors = navColors(),
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(localizedString(R.string.tab_settings)) },
                        colors = navColors(),
                    )
                }
            },
        ) { padding ->
            when (tab) {
                0 -> DiaryNavHost(viewModel, Modifier.padding(padding))
                1 -> MirrorTabScreen(viewModel, Modifier.padding(padding))
                else -> SettingsScreen(viewModel, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = VaultColors.seal,
    selectedTextColor = VaultColors.seal,
    indicatorColor = VaultColors.userBubble,
    unselectedIconColor = VaultColors.inkMuted,
    unselectedTextColor = VaultColors.inkMuted,
)
