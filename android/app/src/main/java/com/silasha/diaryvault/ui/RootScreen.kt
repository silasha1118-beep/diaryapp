package com.silasha.diaryvault.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.silasha.diaryvault.R
import com.silasha.diaryvault.VaultViewModel
import com.silasha.diaryvault.ui.auth.PhoneOTPLoginScreen
import com.silasha.diaryvault.ui.components.localizedString
import com.silasha.diaryvault.ui.diary.DiaryNavHost
import com.silasha.diaryvault.ui.mirror.MirrorTabScreen
import com.silasha.diaryvault.ui.onboarding.OnboardingScreen
import com.silasha.diaryvault.ui.settings.SettingsNavHost
import com.silasha.diaryvault.ui.theme.VaultBackground
import com.silasha.diaryvault.ui.theme.VaultColors

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
    // Once Mirror has been opened, keep it composed (iOS TabView parity) so draft /
    // list scroll survive leaving the tab. Message text also persists via MirrorChatStore.
    var mirrorOpened by rememberSaveable { mutableStateOf(false) }
    val mirrorSelected = tab == 1
    val keepMirrorComposed = mirrorOpened || mirrorSelected
    LaunchedEffect(mirrorSelected) {
        if (mirrorSelected) mirrorOpened = true
    }
    // Hide tab bar while IME is open so nested imePadding (Mirror composer, etc.)
    // is not stacked on top of NavigationBar height (large empty gap above keyboard).
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    VaultBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            // Child tabs own status-bar / top chrome. Zero content insets so nested Scaffolds
            // + FAB are not double-padded against MIUI 3-button nav (Redmi 9C overlap).
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!imeVisible) {
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
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (keepMirrorComposed) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (mirrorSelected) 1f else 0f)
                            .graphicsLayer {
                                alpha = if (mirrorSelected) 1f else 0f
                            },
                    ) {
                        MirrorTabScreen(viewModel, Modifier.fillMaxSize())
                    }
                }
                when (tab) {
                    0 -> DiaryNavHost(viewModel, Modifier.fillMaxSize())
                    2 -> SettingsNavHost(viewModel, Modifier.fillMaxSize())
                }
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
