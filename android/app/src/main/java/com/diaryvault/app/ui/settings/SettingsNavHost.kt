package com.diaryvault.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.diaryvault.app.VaultViewModel

@Composable
fun SettingsNavHost(viewModel: VaultViewModel, modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "settings", modifier = modifier) {
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onOpenRecentlyDeleted = { nav.navigate("recently_deleted") },
            )
        }
        composable("recently_deleted") {
            RecentlyDeletedScreen(
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
