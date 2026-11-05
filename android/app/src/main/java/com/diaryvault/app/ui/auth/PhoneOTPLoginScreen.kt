package com.diaryvault.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.ui.components.VaultBrandHeader
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultBackground
import com.diaryvault.app.ui.theme.VaultColors

/**
 * Fake phone OTP gate — mirrors HarmonyOS Index + iOS AuthGateView / PhoneOTPLoginForm.
 * Any non-empty number → “send” → any 4+ digit code (or stub 000000) → local session.
 */
@Composable
fun PhoneOTPLoginScreen(viewModel: VaultViewModel) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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

            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(localizedString(R.string.auth_phone_label)) },
                placeholder = { Text(localizedString(R.string.auth_phone_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
            )

            if (sent) {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.filter { ch -> ch.isDigit() }.take(6)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(localizedString(R.string.auth_otp_label)) },
                    placeholder = { Text(localizedString(R.string.auth_otp_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                )
                Text(
                    localizedString(R.string.auth_stub_hint, "000000"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.inkMuted,
                )
            }

            error?.let {
                Text(it, color = VaultColors.seal, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    if (!sent) {
                        if (phone.trim().length < 6) {
                            error = viewModel.string(R.string.auth_phone_invalid)
                            return@Button
                        }
                        sent = true
                        error = null
                    } else {
                        if (code.length < 4) {
                            error = viewModel.string(R.string.auth_otp_error)
                            return@Button
                        }
                        viewModel.signInWithPhoneStub(phone.trim())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultColors.seal,
                    contentColor = VaultColors.paper,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    localizedString(
                        if (sent) R.string.auth_otp_verify else R.string.auth_phone_send,
                    ),
                )
            }

            Text(
                localizedString(R.string.auth_preview_note),
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.inkMuted,
            )

            Spacer(Modifier.height(8.dp))
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
