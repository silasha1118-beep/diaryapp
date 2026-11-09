package com.silasha.diaryvault.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.silasha.diaryvault.VaultViewModel
import com.silasha.diaryvault.ui.paywall.PaywallScreen

@Composable
fun SettingsNavHost(viewModel: VaultViewModel, modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "settings", modifier = modifier) {
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onOpenRecentlyDeleted = { nav.navigate("recently_deleted") },
                onOpenPaywall = { nav.navigate("paywall") },
            )
        }
        composable("recently_deleted") {
            RecentlyDeletedScreen(
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
            )
        }
        composable("paywall") {
            PaywallScreen(
                viewModel = viewModel,
                onClose = { nav.popBackStack() },
            )
        }
    }
}
