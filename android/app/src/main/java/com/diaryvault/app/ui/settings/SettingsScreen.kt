package com.diaryvault.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.model.AppLanguage
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultColors

private val CardBg = Color.White
private val CardRadius = 14.dp
private val Destructive = Color(0xFFC0392B)
private val DividerColor = Color(0xFFE8E2D8)

/**
 * Settings sections aligned with iOS SettingsView + HarmonyOS SettingsTab order:
 * 帳號 → 連結手機 → 同步 → 訂閱 → 隱私與資料 → 語言 → 建議 → 金庫 → 法律資訊
 * Customer UI only — no Developer / Firebase backend dumps (matches iOS SettingsDeveloperTools off).
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
    val hasLiveFirebase by viewModel.hasLiveFirebaseAuth.collectAsState()
    val lastSyncAt by viewModel.lastCloudSyncAt.collectAsState()
    val lastSyncError by viewModel.lastCloudSyncError.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var phoneNational by remember { mutableStateOf("") }
    var dialRes by remember { mutableIntStateOf(R.string.auth_dial_tw) }
    var dialExpanded by remember { mutableStateOf(false) }
    var exportReady by remember { mutableStateOf(false) }
    var langExpanded by remember { mutableStateOf(false) }

    val dialOptions = listOf(
        R.string.auth_dial_tw,
        R.string.auth_dial_hk,
        R.string.auth_dial_cn,
        R.string.auth_dial_my,
        R.string.auth_dial_sg,
        R.string.auth_dial_th,
        R.string.auth_dial_jp,
    )
    val dialCode = localizedString(dialRes)
    val syncStatusText = when {
        hasLiveFirebase && lastSyncError != null ->
            localizedString(R.string.settings_cloud_status_error)
        hasLiveFirebase && lastSyncAt != null ->
            localizedString(R.string.settings_cloud_status_synced)
        hasLiveFirebase ->
            localizedString(R.string.settings_cloud_status_pending)
        accountSignedIn ->
            localizedString(R.string.settings_cloud_status_local_session)
        else ->
            localizedString(R.string.settings_cloud_status_signed_out)
    }
    val linkedPhone = viewModel.accountPhone != null
    val linkedApple = false // Android stub — Apple Sign-In not wired
    val identity = accountLabel.ifBlank { name }.ifBlank { "Silas" }
    val planName = if (billingActive) {
        viewModel.billingPlanName
    } else {
        localizedString(R.string.settings_billing_active)
    }
    val periodEnd = localizedString(R.string.settings_billing_period_stub)

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        localizedString(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp),
        ) {
            // 1. 帳號
            SettingsCard(header = localizedString(R.string.settings_section_account)) {
                SettingsRow(
                    localizedString(R.string.settings_account_status),
                    localizedString(
                        if (accountSignedIn) R.string.settings_account_signed_in
                        else R.string.settings_account_signed_out,
                    ),
                )
                SettingsDivider()
                SettingsRow(localizedString(R.string.settings_account_identity), identity)
                SettingsDivider()
                SettingsRow(
                    localizedString(R.string.settings_account_provider),
                    if (accountSignedIn) viewModel.accountProviderLabel
                    else "—",
                )
                SettingsDivider()
                SettingsRow(
                    localizedString(R.string.settings_account_linked_phone),
                    localizedString(
                        if (linkedPhone) R.string.settings_account_linked_yes
                        else R.string.settings_account_linked_no,
                    ),
                    valueColor = if (linkedPhone) VaultColors.inkMuted else VaultColors.seal,
                )
                SettingsDivider()
                SettingsRow(
                    localizedString(R.string.settings_account_linked_apple),
                    localizedString(
                        if (linkedApple) R.string.settings_account_linked_yes
                        else R.string.settings_account_linked_no,
                    ),
                    valueColor = if (linkedApple) VaultColors.inkMuted else VaultColors.seal,
                )
                if (accountSignedIn) {
                    SettingsDivider()
                    SettingsActionRow(
                        title = localizedString(R.string.settings_account_sign_out),
                        color = Destructive,
                        onClick = viewModel::signOutAccount,
                    )
                }
            }

            // 2. 連結手機 — mirrors iOS linkSections + PhoneOTPLoginForm(.settings, .link)
            // No Firebase/Stub provider caption (iOS hides that outside DEBUG).
            SettingsCard(
                header = localizedString(R.string.settings_section_link_phone),
                footer = localizedString(R.string.settings_link_phone_note),
            ) {
                Text(
                    localizedString(R.string.settings_link_phone_banner),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                    ),
                    color = VaultColors.seal,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp),
                )
                Text(
                    localizedString(R.string.auth_phone_label),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = VaultColors.inkMuted,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExposedDropdownMenuBox(
                        expanded = dialExpanded,
                        onExpandedChange = { dialExpanded = it },
                        modifier = Modifier.weight(0.42f),
                    ) {
                        OutlinedTextField(
                            value = dialCode,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dialExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = creamFieldColors(),
                            shape = RoundedCornerShape(4.dp),
                            textStyle = MaterialTheme.typography.labelLarge.copy(color = VaultColors.ink),
                        )
                        ExposedDropdownMenu(
                            expanded = dialExpanded,
                            onDismissRequest = { dialExpanded = false },
                        ) {
                            dialOptions.forEach { res ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            localizedString(res),
                                            color = VaultColors.ink,
                                        )
                                    },
                                    onClick = {
                                        dialRes = res
                                        dialExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = phoneNational,
                        onValueChange = { phoneNational = it.filter { ch -> ch.isDigit() || ch == ' ' } },
                        modifier = Modifier.weight(0.58f),
                        placeholder = {
                            Text(
                                localizedString(R.string.auth_phone_placeholder),
                                color = VaultColors.inkMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        singleLine = true,
                        colors = creamFieldColors(),
                        shape = RoundedCornerShape(4.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = VaultColors.ink),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }
                // iOS: Color.white on VaultTheme.seal — cream paper as contentColor washed out on burgundy.
                Button(
                    onClick = { /* UI stub — SMS link wiring later */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultColors.seal,
                        contentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                    ),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        localizedString(R.string.settings_link_phone_send),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }

            // 3. 同步
            SettingsCard(
                header = localizedString(R.string.settings_section_cloud),
                footer = localizedString(R.string.settings_cloud_footer),
            ) {
                SettingsRow(
                    localizedString(R.string.settings_cloud_status),
                    syncStatusText,
                )
                if (!lastSyncError.isNullOrBlank()) {
                    SettingsDivider()
                    Text(
                        lastSyncError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultColors.seal,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                SettingsDivider()
                SettingsActionRow(
                    title = localizedString(
                        if (isSyncing) R.string.settings_cloud_syncing
                        else R.string.settings_cloud_sync_now,
                    ),
                    color = if (hasLiveFirebase) VaultColors.seal else VaultColors.inkMuted,
                    onClick = {
                        if (hasLiveFirebase && !isSyncing) viewModel.syncNow()
                    },
                )
            }

            // 4. 訂閱
            SettingsCard(
                header = localizedString(R.string.settings_section_billing),
                footer = localizedString(R.string.settings_billing_footer),
            ) {
                SettingsRow(localizedString(R.string.settings_billing_plan), planName)
                SettingsDivider()
                SettingsRow(localizedString(R.string.settings_billing_expires), periodEnd)
                SettingsDivider()
                SettingsActionRow(
                    title = localizedString(R.string.settings_billing_manage),
                    color = VaultColors.seal,
                    onClick = { /* stub paywall */ },
                )
            }

            // 5. 隱私與資料
            SettingsCard(
                header = localizedString(R.string.settings_section_data),
                footer = localizedString(R.string.settings_data_footer),
            ) {
                SettingsActionRow(
                    title = localizedString(R.string.settings_data_export),
                    color = VaultColors.seal,
                    onClick = { exportReady = true },
                )
                if (exportReady) {
                    SettingsDivider()
                    SettingsActionRow(
                        title = localizedString(R.string.settings_data_export_share),
                        color = VaultColors.seal,
                        onClick = { /* stub share */ },
                    )
                }
                SettingsDivider()
                SettingsActionRow(
                    title = localizedString(R.string.settings_data_delete),
                    color = Destructive,
                    onClick = { /* stub delete */ },
                )
            }

            // 6. 語言
            SettingsCard(
                header = localizedString(R.string.settings_section_language),
                footer = localizedString(R.string.settings_language_footer),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        localizedString(R.string.settings_language),
                        style = MaterialTheme.typography.bodyLarge,
                        color = VaultColors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    ExposedDropdownMenuBox(
                        expanded = langExpanded,
                        onExpandedChange = { langExpanded = it },
                    ) {
                        Text(
                            text = languageDisplayName(language),
                            style = MaterialTheme.typography.bodyMedium,
                            color = VaultColors.inkMuted,
                            modifier = Modifier
                                .menuAnchor()
                                .clickable { langExpanded = true }
                                .padding(vertical = 4.dp),
                        )
                        ExposedDropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false },
                        ) {
                            AppLanguage.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(languageDisplayName(lang)) },
                                    onClick = {
                                        viewModel.setLanguage(lang)
                                        langExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // 7. 建議
            SettingsCard(
                header = localizedString(R.string.settings_section_advice),
                footer = localizedString(R.string.settings_reminders_footer),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        localizedString(R.string.settings_reminders),
                        style = MaterialTheme.typography.bodyLarge,
                        color = VaultColors.ink,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Switch(
                        checked = reminders,
                        onCheckedChange = viewModel::setGentleReminders,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = VaultColors.seal,
                            checkedThumbColor = VaultColors.paper,
                        ),
                    )
                }
            }

            // 8. 金庫
            SettingsCard(header = localizedString(R.string.settings_section_vault)) {
                SettingsRow(
                    localizedString(R.string.settings_name),
                    name.ifBlank { "Silas" },
                    valueColor = VaultColors.ink,
                )
                SettingsDivider()
                Text(
                    localizedString(R.string.settings_legacy_stub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.inkMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            // 9. 法律資訊
            SettingsCard(
                header = localizedString(R.string.settings_section_legal),
                footer = localizedString(R.string.settings_legal_footer),
            ) {
                SettingsActionRow(
                    title = localizedString(R.string.settings_legal_privacy),
                    color = VaultColors.seal,
                    onClick = { /* stub */ },
                )
                SettingsDivider()
                SettingsActionRow(
                    title = localizedString(R.string.settings_legal_terms),
                    color = VaultColors.seal,
                    onClick = { /* stub */ },
                )
            }

        }
    }
}

@Composable
private fun languageDisplayName(language: AppLanguage): String = when (language) {
    AppLanguage.ZhHant -> localizedString(R.string.lang_zh_hant_full)
    AppLanguage.ZhHans -> localizedString(R.string.lang_zh_hans_full)
    AppLanguage.English -> localizedString(R.string.lang_en_full)
}

@Composable
private fun creamFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VaultColors.seal,
    unfocusedBorderColor = VaultColors.line,
    focusedContainerColor = VaultColors.paper,
    unfocusedContainerColor = VaultColors.paper,
    disabledContainerColor = VaultColors.paper,
    cursorColor = VaultColors.seal,
    focusedTextColor = VaultColors.ink,
    unfocusedTextColor = VaultColors.ink,
    focusedPlaceholderColor = VaultColors.inkMuted,
    unfocusedPlaceholderColor = VaultColors.inkMuted,
)

@Composable
private fun SettingsCard(
    header: String,
    footer: String = "",
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 18.dp),
    ) {
        Text(
            header,
            style = MaterialTheme.typography.labelLarge,
            color = VaultColors.inkMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(CardBg, RoundedCornerShape(CardRadius)),
        ) {
            content()
        }
        if (footer.isNotEmpty()) {
            Text(
                footer,
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.inkMuted,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    valueColor: Color = VaultColors.inkMuted,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = VaultColors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    color: Color,
    onClick: () -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = DividerColor,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp),
    )
}
