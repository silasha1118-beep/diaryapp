package com.diaryvault.app.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diaryvault.app.BuildConfig
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.config.AuthConfig
import com.diaryvault.app.ui.components.VaultBrandHeader
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultBackground
import com.diaryvault.app.ui.theme.VaultColors

/**
 * Phone SMS OTP gate — Firebase when `google-services.json` is present, else local stub.
 * Mirrors iOS AuthGateView / PhoneOTPLoginForm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneOTPLoginScreen(viewModel: VaultViewModel) {
    val activity = LocalContext.current as ComponentActivity
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var dialCode by remember { mutableStateOf(activity.getString(R.string.auth_dial_tw)) }
    var dialExpanded by remember { mutableStateOf(false) }

    val session by viewModel.otpSession.collectAsState()
    val busy by viewModel.authBusy.collectAsState()
    val error by viewModel.authError.collectAsState()
    val sent = session != null

    val dialOptions = listOf(
        R.string.auth_dial_tw,
        R.string.auth_dial_hk,
        R.string.auth_dial_cn,
        R.string.auth_dial_my,
        R.string.auth_dial_sg,
        R.string.auth_dial_th,
        R.string.auth_dial_jp,
    )

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VaultColors.seal,
        unfocusedBorderColor = VaultColors.line,
        focusedTextColor = VaultColors.ink,
        cursorColor = VaultColors.seal,
        focusedContainerColor = VaultColors.paper.copy(alpha = 0.6f),
        unfocusedContainerColor = VaultColors.paper.copy(alpha = 0.45f),
    )

    VaultBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VaultBrandHeader(
                title = localizedString(R.string.brand_name),
                subtitle = localizedString(R.string.auth_tagline),
            )

            Text(
                localizedString(R.string.auth_headline),
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal,
                color = VaultColors.ink,
            )
            Text(
                localizedString(R.string.auth_body),
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.inkMuted,
            )

            if (BuildConfig.DEBUG) {
                Text(
                    localizedString(
                        if (viewModel.smsProviderKind == AuthConfig.SMSProviderKind.FIREBASE) {
                            R.string.auth_sms_provider_firebase
                        } else {
                            R.string.auth_sms_provider_stub
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = VaultColors.inkMuted,
                )
            }

            Text(
                localizedString(R.string.auth_phone_label),
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.inkMuted,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExposedDropdownMenuBox(
                    expanded = dialExpanded,
                    onExpandedChange = { dialExpanded = it },
                    modifier = Modifier.weight(0.45f),
                ) {
                    OutlinedTextField(
                        value = dialCode,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        enabled = !sent && !busy,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dialExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.labelLarge,
                    )
                    ExposedDropdownMenu(
                        expanded = dialExpanded,
                        onDismissRequest = { dialExpanded = false },
                    ) {
                        dialOptions.forEach { res ->
                            val label = localizedString(res)
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    dialCode = label
                                    dialExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it.filter { ch -> ch.isDigit() || ch == '+' }
                        viewModel.clearAuthError()
                    },
                    modifier = Modifier.weight(0.55f),
                    enabled = !sent && !busy,
                    placeholder = { Text(localizedString(R.string.auth_phone_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            if (sent) {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.filter { ch -> ch.isDigit() }.take(6)
                        viewModel.clearAuthError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    label = { Text(localizedString(R.string.auth_otp_label)) },
                    placeholder = { Text(localizedString(R.string.auth_otp_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                )
                session?.stubDebugHint?.let { hint ->
                    Text(
                        localizedString(R.string.auth_stub_hint, hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultColors.inkMuted,
                    )
                }
            }

            error?.let {
                Text(it, color = VaultColors.seal, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    if (!sent) {
                        if (phone.trim().length < 6) {
                            viewModel.clearAuthError()
                            // Surface via local validation through sendPhoneCode path
                        }
                        viewModel.sendPhoneCode(activity, phone.trim(), dialCode)
                    } else {
                        viewModel.verifyPhoneCode(code)
                    }
                },
                enabled = !busy && (if (!sent) phone.trim().length >= 6 else code.length >= 4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultColors.seal,
                    contentColor = VaultColors.paper,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = VaultColors.paper,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        localizedString(
                            if (sent) R.string.auth_otp_verify else R.string.auth_phone_send,
                        ),
                    )
                }
            }

            if (sent) {
                TextButton(
                    onClick = {
                        code = ""
                        viewModel.resetOtpSession()
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        localizedString(R.string.auth_phone_change_number),
                        color = VaultColors.inkMuted,
                    )
                }
            }

            if (viewModel.smsProviderKind != AuthConfig.SMSProviderKind.FIREBASE) {
                Text(
                    localizedString(R.string.auth_preview_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.inkMuted,
                )
            }

            Spacer(Modifier.height(8.dp))
            if (BuildConfig.DEBUG) {
                TextButton(
                    onClick = { viewModel.signInLocalStub() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        localizedString(R.string.auth_dev_local),
                        color = VaultColors.inkMuted,
                    )
                }
            }
        }
    }
}
