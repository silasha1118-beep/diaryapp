package com.diaryvault.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.billing.BillingConfig
import com.diaryvault.app.model.AppLanguage
import com.diaryvault.app.ui.components.AuthDialPhoneRow
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultColors
import java.text.DateFormat
import java.util.Date

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
fun SettingsScreen(
    viewModel: VaultViewModel,
    onOpenRecentlyDeleted: () -> Unit,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language by viewModel.language.collectAsState()
    val reminders by viewModel.gentleReminders.collectAsState()
    val name by viewModel.displayName.collectAsState()
    val accountSignedIn by viewModel.accountSignedIn.collectAsState()
    val accountLabel by viewModel.accountLabel.collectAsState()
    val billingActive by viewModel.billingActive.collectAsState()
    val billingTier by viewModel.billingTier.collectAsState()
    val billingExpirationMs by viewModel.billingExpirationMs.collectAsState()
    val hasLiveFirebase by viewModel.hasLiveFirebaseAuth.collectAsState()
    val lastSyncAt by viewModel.lastCloudSyncAt.collectAsState()
    val lastSyncError by viewModel.lastCloudSyncError.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val recentlyDeleted by viewModel.recentlyDeletedEntries.collectAsState()
    val recentlyDeletedCount = recentlyDeleted.size

    var phoneNational by remember { mutableStateOf("") }
    var dialRes by remember { mutableIntStateOf(R.string.auth_dial_tw) }
    var dialExpanded by remember { mutableStateOf(false) }
    var exportReady by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val dialOptionRes = listOf(
        R.string.auth_dial_tw,
        R.string.auth_dial_hk,
        R.string.auth_dial_cn,
        R.string.auth_dial_my,
        R.string.auth_dial_sg,
        R.string.auth_dial_th,
        R.string.auth_dial_jp,
    )
    val dialLabels = dialOptionRes.map { res -> res to localizedString(res) }
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
    val identity = accountLabel.ifBlank { name }.ifBlank { "Silas" }
    val planName = when (billingTier) {
        BillingConfig.Tier.FREE -> localizedString(R.string.settings_billing_free)
        BillingConfig.Tier.AUTHOR_PLUS -> localizedString(R.string.settings_billing_active)
        BillingConfig.Tier.AUTHOR_PRO -> localizedString(R.string.settings_billing_active_pro)
    }
    val periodEnd = billingExpirationMs?.let { ms ->
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
    } ?: if (billingActive) {
        localizedString(R.string.settings_billing_period_play)
    } else {
        localizedString(R.string.settings_billing_period_none)
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        // MainTabs owns bottom nav insets; TopAppBar still applies statusBars via defaults.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                .padding(bottom = 24.dp),
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
                // Android: no 「已連結 Apple」 — Sign in with Apple is iOS-only backup.
                if (accountSignedIn) {
                    SettingsDivider()
                    SettingsActionRow(
                        title = localizedString(R.string.settings_account_sign_out),
                        color = Destructive,
                        onClick = viewModel::signOutAccount,
                    )
                }
            }

            // 2. 連結手機 — Android is SMS/phone primary; hide when already phone-linked.
            // Banner is phone-appropriate (not Apple-login copy). No Apple link section here.
            if (!linkedPhone) {
                SettingsCard(
                    header = localizedString(R.string.settings_section_link_phone),
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
                    AuthDialPhoneRow(
                        dialLabel = dialCode,
                        dialExpanded = dialExpanded,
                        onDialExpandedChange = { dialExpanded = it },
                        dialOptions = dialLabels.map { it.second },
                        onDialSelected = { label ->
                            dialRes = dialLabels.firstOrNull { it.second == label }?.first
                                ?: R.string.auth_dial_tw
                        },
                        phone = phoneNational,
                        onPhoneChange = { phoneNational = it.filter { ch -> ch.isDigit() || ch == ' ' } },
                        phonePlaceholder = localizedString(R.string.auth_phone_placeholder),
                        enabled = true,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        corner = 4.dp,
                    )
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
            }

            // 3. 同步
            SettingsCard(
                header = localizedString(R.string.settings_section_cloud),
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
            ) {
                SettingsRow(localizedString(R.string.settings_billing_plan), planName)
                SettingsDivider()
                SettingsRow(localizedString(R.string.settings_billing_expires), periodEnd)
                SettingsDivider()
                SettingsActionRow(
                    title = localizedString(R.string.settings_billing_manage),
                    color = VaultColors.seal,
                    onClick = onOpenPaywall,
                )
            }

            // 5. 隱私與資料
            SettingsCard(
                header = localizedString(R.string.settings_section_data),
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

            // 6. 語言 — tap row → dialog choices (no nested ExposedDropdown frames)
            SettingsCard(
                header = localizedString(R.string.settings_section_language),
            ) {
                SettingsNavRow(
                    title = localizedString(R.string.settings_language),
                    value = languageDisplayName(language),
                    onClick = { showLanguageDialog = true },
                )
            }

            // 7. 建議
            SettingsCard(
                header = localizedString(R.string.settings_section_advice),
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

            // 8. 金庫 — display name + drill-in to soft-delete restore (30 days).
            SettingsCard(
                header = localizedString(R.string.settings_section_vault),
            ) {
                SettingsRow(
                    localizedString(R.string.settings_name),
                    name.ifBlank { "Silas" },
                    valueColor = VaultColors.ink,
                )
                SettingsDivider()
                SettingsNavRow(
                    title = localizedString(R.string.settings_vault_recently_deleted),
                    value = if (recentlyDeletedCount > 0) recentlyDeletedCount.toString() else "",
                    onClick = onOpenRecentlyDeleted,
                )
            }

            // 9. 法律資訊 — privacy / terms stay as primary actions
            SettingsCard(
                header = localizedString(R.string.settings_section_legal),
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

    if (showLanguageDialog) {
        LanguagePickerDialog(
            selected = language,
            onSelect = { lang ->
                viewModel.setLanguage(lang)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                localizedString(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                color = VaultColors.ink,
            )
        },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { lang ->
                    val checked = lang == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = checked,
                                onClick = { onSelect(lang) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = checked,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = VaultColors.seal,
                                unselectedColor = VaultColors.inkMuted,
                            ),
                        )
                        Text(
                            languageDisplayName(lang),
                            style = MaterialTheme.typography.bodyLarge,
                            color = VaultColors.ink,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(localizedString(R.string.common_cancel), color = VaultColors.seal)
            }
        },
        containerColor = VaultColors.paper,
    )
}

@Composable
private fun languageDisplayName(language: AppLanguage): String = when (language) {
    AppLanguage.ZhHant -> localizedString(R.string.lang_zh_hant_full)
    AppLanguage.ZhHans -> localizedString(R.string.lang_zh_hans_full)
    AppLanguage.English -> localizedString(R.string.lang_en_full)
    AppLanguage.Japanese -> localizedString(R.string.lang_ja_full)
    AppLanguage.Thai -> localizedString(R.string.lang_th_full)
}

@Composable
private fun SettingsCard(
    header: String,
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = VaultColors.ink,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
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
private fun SettingsNavRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = VaultColors.ink,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (value.isNotEmpty()) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "›",
            style = MaterialTheme.typography.bodyLarge,
            color = VaultColors.inkMuted,
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = DividerColor,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp),
    )
}
