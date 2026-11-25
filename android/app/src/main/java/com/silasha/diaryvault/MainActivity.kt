package com.silasha.diaryvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.silasha.diaryvault.ui.RootScreen
import com.silasha.diaryvault.ui.theme.DiaryVaultTheme

class MainActivity : ComponentActivity() {
    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val language by viewModel.language.collectAsState()
            DiaryVaultTheme(language = language) {
                RootScreen(viewModel = viewModel)
            }
        }
    }
}
