package com.diaryvault.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.ui.components.LanguageStrip
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: VaultViewModel, modifier: Modifier = Modifier) {
    val language by viewModel.language.collectAsState()
    val reminders by viewModel.gentleReminders.collectAsState()
    val name by viewModel.displayName.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(localizedString(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(localizedString(R.string.settings_section_language))
            Text(localizedString(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
            LanguageStrip(selected = language, onSelect = viewModel::setLanguage)
            Footer(localizedString(R.string.settings_language_footer))

            Divider()

            SectionTitle(localizedString(R.string.settings_section_advice))
            RowSwitch(
                title = localizedString(R.string.settings_reminders),
                checked = reminders,
                onCheckedChange = viewModel::setGentleReminders,
            )
            Footer(localizedString(R.string.settings_reminders_footer))

            Divider()

            SectionTitle(localizedString(R.string.settings_section_ai))
            Text(
                localizedString(R.string.settings_ai_mode) + ": " +
                    localizedString(if (viewModel.useCloudClient) R.string.settings_ai_cloud else R.string.settings_ai_mock),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (viewModel.useCloudClient) {
                Text(
                    localizedString(R.string.settings_ai_provider) + ": " +
                        localizedString(
                            if (viewModel.isAnthropicProvider) {
                                R.string.settings_ai_provider_anthropic
                            } else {
                                R.string.settings_ai_provider_openai
                            },
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                localizedString(R.string.settings_ai_residency) + ": " + viewModel.dataResidency,
                style = MaterialTheme.typography.bodyLarge,
            )
            Footer(localizedString(R.string.settings_ai_footer))

            Divider()

            SectionTitle(localizedString(R.string.settings_section_legacy))
            Text(localizedString(R.string.settings_legacy_stub), style = MaterialTheme.typography.bodyLarge)
            Footer(localizedString(R.string.settings_legacy_footer))

            Divider()

            SectionTitle(localizedString(R.string.settings_section_vault))
            Text(localizedString(R.string.settings_name), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = name,
                onValueChange = viewModel::setDisplayName,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultColors.seal,
                    unfocusedBorderColor = VaultColors.line,
                    cursorColor = VaultColors.seal,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = viewModel::resetOnboarding) {
                Text(localizedString(R.string.settings_reset_onboarding), color = VaultColors.seal)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = VaultColors.seal)
}

@Composable
private fun Footer(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = VaultColors.line.copy(alpha = 0.6f))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RowSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = VaultColors.seal,
                checkedThumbColor = VaultColors.paper,
            ),
        )
    }
}
