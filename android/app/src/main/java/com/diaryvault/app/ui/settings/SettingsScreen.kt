package com.diaryvault.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.ui.components.LanguageStrip
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultColors

/**
 * Settings sections aligned with iOS SettingsView (UI-only stubs where backend is missing):
 * account, link phone, sync, billing, privacy/data, language, reminders, vault name, legal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: VaultViewModel, modifier: Modifier = Modifier) {
    val language by viewModel.language.collectAsState()
    val reminders by viewModel.gentleReminders.collectAsState()
    val name by viewModel.displayName.collectAsState()
    val accountSignedIn by viewModel.accountSignedIn.collectAsState()
    val accountLabel by viewModel.accountLabel.collectAsState()
    val billingActive by viewModel.billingActive.collectAsState()
    var linkPhone by remember { mutableStateOf("") }
    var syncStatus by remember { mutableStateOf(R.string.settings_cloud_status_local) }
    var exportReady by remember { mutableStateOf(false) }

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
            // Account
            SectionTitle(localizedString(R.string.settings_section_account))
            LabeledRow(
                localizedString(R.string.settings_account_status),
                localizedString(
                    if (accountSignedIn) R.string.settings_account_signed_in
                    else R.string.settings_account_signed_out,
                ),
            )
            if (accountLabel.isNotBlank()) {
                LabeledRow(localizedString(R.string.settings_account_identity), accountLabel)
            }
            if (accountSignedIn) {
                LabeledRow(
                    localizedString(R.string.settings_account_provider),
                    viewModel.accountProviderLabel,
                )
                viewModel.accountPhone?.let { phone ->
                    LabeledRow(localizedString(R.string.settings_account_linked_phone), phone)
                }
                TextButton(onClick = viewModel::signOutAccount) {
                    Text(localizedString(R.string.settings_account_sign_out), color = Color(0xFFB3261E))
                }
            }
            Footer(localizedString(R.string.settings_account_footer))
            Divider()

            // Link phone stub
            SectionTitle(localizedString(R.string.settings_section_link_phone))
            Text(
                localizedString(R.string.settings_link_phone_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.seal,
            )
            OutlinedTextField(
                value = linkPhone,
                onValueChange = { linkPhone = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(localizedString(R.string.auth_phone_placeholder)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultColors.seal,
                    unfocusedBorderColor = VaultColors.line,
                    cursorColor = VaultColors.seal,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            TextButton(onClick = { /* stub */ }) {
                Text(localizedString(R.string.settings_link_phone_send), color = VaultColors.seal)
            }
            Footer(localizedString(R.string.settings_link_phone_note))
            Divider()

            // Sync
            SectionTitle(localizedString(R.string.settings_section_cloud))
            LabeledRow(
                localizedString(R.string.settings_cloud_status),
                localizedString(syncStatus),
            )
            TextButton(onClick = { syncStatus = R.string.settings_cloud_status_synced }) {
                Text(localizedString(R.string.settings_cloud_sync_now), color = VaultColors.seal)
            }
            Footer(localizedString(R.string.settings_cloud_footer))
            Divider()

            // Billing
            SectionTitle(localizedString(R.string.settings_section_billing))
            LabeledRow(
                localizedString(R.string.settings_billing_plan),
                if (billingActive) viewModel.billingPlanName
                else localizedString(R.string.settings_billing_free),
            )
            TextButton(onClick = { /* stub paywall */ }) {
                Text(localizedString(R.string.settings_billing_manage), color = VaultColors.seal)
            }
            Footer(localizedString(R.string.settings_billing_footer))
            Divider()

            // Privacy / data
            SectionTitle(localizedString(R.string.settings_section_data))
            TextButton(onClick = { exportReady = true }) {
                Text(localizedString(R.string.settings_data_export), color = VaultColors.seal)
            }
            if (exportReady) {
                Text(
                    localizedString(R.string.settings_data_export_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.inkMuted,
                )
            }
            TextButton(onClick = { /* stub */ }) {
                Text(localizedString(R.string.settings_data_delete), color = Color(0xFFB3261E))
            }
            Footer(localizedString(R.string.settings_data_footer))
            Divider()

            // Language
            SectionTitle(localizedString(R.string.settings_section_language))
            Text(localizedString(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
            LanguageStrip(selected = language, onSelect = viewModel::setLanguage)
            Footer(localizedString(R.string.settings_language_footer))
            Divider()

            // Reminders
            SectionTitle(localizedString(R.string.settings_section_advice))
            RowSwitch(
                title = localizedString(R.string.settings_reminders),
                checked = reminders,
                onCheckedChange = viewModel::setGentleReminders,
            )
            Footer(localizedString(R.string.settings_reminders_footer))
            Divider()

            // Vault name
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
            Text(
                localizedString(R.string.settings_legacy_stub),
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.inkMuted,
            )
            Divider()

            // Legal
            SectionTitle(localizedString(R.string.settings_section_legal))
            TextButton(onClick = { /* stub */ }) {
                Text(localizedString(R.string.settings_legal_privacy), color = VaultColors.seal)
            }
            TextButton(onClick = { /* stub */ }) {
                Text(localizedString(R.string.settings_legal_terms), color = VaultColors.seal)
            }
            Footer(localizedString(R.string.settings_legal_footer))

            Spacer(Modifier.height(8.dp))
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
private fun LabeledRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = VaultColors.inkMuted)
    }
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = VaultColors.line.copy(alpha = 0.6f))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RowSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
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
