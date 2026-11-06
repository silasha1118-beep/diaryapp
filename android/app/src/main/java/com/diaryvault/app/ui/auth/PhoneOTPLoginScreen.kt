package com.diaryvault.app.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diaryvault.app.BuildConfig
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.auth.SmsAuthPath
import com.diaryvault.app.config.AuthConfig
import com.diaryvault.app.ui.components.AuthCreamField
import com.diaryvault.app.ui.components.AuthDialPhoneRow
import com.diaryvault.app.ui.components.VaultBrandHeader
import com.diaryvault.app.ui.components.dialCodeFromLabel
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultBackground
import com.diaryvault.app.ui.theme.VaultColors
import kotlinx.coroutines.delay

/**
 * Phone SMS OTP gate — dial **+86** → Aliyun in release when configured;
 * **DEBUG +86** (and missing Aliyun keys) → Firebase Phone Auth (Console test numbers);
 * other dials → Firebase Phone Auth when `google-services.json` is present, else local stub.
 * Customer UI only — no DEBUG / stub / local-session chrome (prefs: no developer chrome).
 * Mirrors iOS AuthGateView / PhoneOTPLoginForm.
 */
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
    // Firebase Phone Auth may open Play Integrity / reCAPTCHA; real Aliyun +86 does not.
    // DEBUG routes +86 to Firebase so Console test numbers work.
    val usesFirebaseAppVerification =
        viewModel.smsProviderKind == AuthConfig.SMSProviderKind.FIREBASE &&
            when {
                sent -> session?.path == SmsAuthPath.FIREBASE
                else -> dialCodeFromLabel(dialCode) != "+86" || BuildConfig.DEBUG
            }

    var resendCooldown by remember { mutableIntStateOf(0) }
    LaunchedEffect(session?.verificationID) {
        if (session != null) {
            resendCooldown = 60
            while (resendCooldown > 0) {
                delay(1000)
                resendCooldown--
            }
        } else {
            resendCooldown = 0
        }
    }

    val dialOptionRes = listOf(
        R.string.auth_dial_tw,
        R.string.auth_dial_hk,
        R.string.auth_dial_cn,
        R.string.auth_dial_my,
        R.string.auth_dial_sg,
        R.string.auth_dial_th,
        R.string.auth_dial_jp,
    )
    val dialOptions = dialOptionRes.map { localizedString(it) }
    // Redmi 9C-class (~360×640–800dp usable): shrink brand + spacers so OTP + CTA stay on-screen.
    val compact = LocalConfiguration.current.screenHeightDp < 720
    val sectionGap = if (compact) 18.dp else 28.dp
    val headlineSize = if (compact) 20.sp else 22.sp

    VaultBackground {
        Column(
            Modifier
                .fillMaxSize()
                // enableEdgeToEdge() — pad status / nav / cutout / IME (fixed 48.dp top was not enough on MIUI).
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = if (compact) 12.dp else 20.dp, bottom = 24.dp),
        ) {
            VaultBrandHeader(
                title = localizedString(R.string.brand_name),
                subtitle = localizedString(R.string.auth_tagline),
                compact = compact,
            )

            Spacer(Modifier.height(sectionGap))

            Text(
                localizedString(R.string.auth_headline),
                fontFamily = FontFamily.Serif,
                fontSize = headlineSize,
                fontWeight = FontWeight.Normal,
                color = VaultColors.ink,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
            Text(
                localizedString(R.string.auth_body),
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.inkMuted,
            )

            Spacer(Modifier.height(sectionGap))

            Text(
                localizedString(R.string.auth_phone_label),
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.inkMuted,
            )
            Spacer(Modifier.height(8.dp))
            AuthDialPhoneRow(
                dialLabel = dialCode,
                dialExpanded = dialExpanded,
                onDialExpandedChange = { dialExpanded = it },
                dialOptions = dialOptions,
                onDialSelected = { dialCode = it },
                phone = phone,
                onPhoneChange = {
                    phone = it.filter { ch -> ch.isDigit() || ch == '+' }
                    viewModel.clearAuthError()
                },
                phonePlaceholder = localizedString(R.string.auth_phone_placeholder),
                enabled = !sent && !busy,
            )

            if (sent) {
                Spacer(Modifier.height(16.dp))
                // Label sits above the cream box (same rhythm as 手機號碼), not as a floating
                // OutlinedTextField label that MIUI force-dark turned into a near-black field.
                AuthCreamField(
                    value = code,
                    onValueChange = {
                        code = it.filter { ch -> ch.isDigit() }.take(6)
                        viewModel.clearAuthError()
                    },
                    enabled = !busy,
                    label = localizedString(R.string.auth_otp_label),
                    placeholder = localizedString(R.string.auth_otp_placeholder),
                    keyboardType = KeyboardType.Number,
                )
                if (session?.path == SmsAuthPath.FIREBASE || session?.path == SmsAuthPath.ALIYUN) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        localizedString(R.string.auth_sms_sent_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultColors.inkMuted,
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = VaultColors.seal, style = MaterialTheme.typography.bodyMedium)
            }

            if (busy && !sent && usesFirebaseAppVerification) {
                Spacer(Modifier.height(12.dp))
                Text(
                    localizedString(R.string.auth_sms_sending_security),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.inkMuted,
                )
            }

            Spacer(Modifier.height(if (compact) 18.dp else 24.dp))
            // Theme typography hardcodes ink — must set label color explicitly or seal CTA
            // becomes illegible (dark-on-dark on Redmi). White on seal matches Settings link CTA.
            Button(
                onClick = {
                    if (!sent) {
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
                    contentColor = Color.White,
                    disabledContainerColor = VaultColors.seal.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.85f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        localizedString(
                            if (sent) R.string.auth_otp_verify else R.string.auth_phone_send,
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (sent) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        code = ""
                        viewModel.sendPhoneCode(activity, phone.trim(), dialCode, resend = true)
                    },
                    enabled = !busy && resendCooldown == 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (resendCooldown > 0) {
                            localizedString(R.string.auth_phone_resend_in, resendCooldown)
                        } else {
                            localizedString(R.string.auth_phone_resend)
                        },
                        color = VaultColors.inkMuted,
                    )
                }
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
        }
    }
}
