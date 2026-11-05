package com.diaryvault.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diaryvault.app.ui.theme.VaultColors

/** Shared dial + national-number row height so borders align. */
val AuthPhoneFieldHeight = 52.dp

/**
 * Unified country-dial + phone row — one border, shared height/padding (iOS-style chrome).
 * Dial closed state shows the E.164 code only (e.g. `+852`); menu lists full labels.
 */
@Composable
fun AuthDialPhoneRow(
    dialLabel: String,
    dialExpanded: Boolean,
    onDialExpandedChange: (Boolean) -> Unit,
    dialOptions: List<String>,
    onDialSelected: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    phonePlaceholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
) {
    val shape = RoundedCornerShape(corner)
    val borderColor = VaultColors.line
    val fill = VaultColors.paper.copy(alpha = 0.5f)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = if (enabled) VaultColors.ink else VaultColors.inkMuted,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )
    val dialCode = dialCodeFromLabel(dialLabel)

    Row(
        modifier
            .fillMaxWidth()
            .height(AuthPhoneFieldHeight)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(fill, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(108.dp),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .clickable(enabled = enabled) { onDialExpandedChange(true) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    dialCode,
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = VaultColors.inkMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
            DropdownMenu(
                expanded = dialExpanded,
                onDismissRequest = { onDialExpandedChange(false) },
            ) {
                dialOptions.forEach { label ->
                    DropdownMenuItem(
                        text = {
                            Text(label, color = VaultColors.ink)
                        },
                        onClick = {
                            onDialSelected(label)
                            onDialExpandedChange(false)
                        },
                    )
                }
            }
        }

        Box(
            Modifier
                .width(1.dp)
                .height(28.dp)
                .background(VaultColors.line),
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (phone.isEmpty()) {
                Text(
                    phonePlaceholder,
                    style = textStyle.copy(color = VaultColors.inkMuted, fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = phone,
                onValueChange = onPhoneChange,
                enabled = enabled,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(VaultColors.seal),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Matched OutlinedTextField chrome for OTP / standalone fields on the auth gate. */
@Composable
fun authOutlinedFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VaultColors.seal,
    unfocusedBorderColor = VaultColors.line,
    focusedTextColor = VaultColors.ink,
    unfocusedTextColor = VaultColors.ink,
    cursorColor = VaultColors.seal,
    focusedContainerColor = VaultColors.paper.copy(alpha = 0.6f),
    unfocusedContainerColor = VaultColors.paper.copy(alpha = 0.45f),
    focusedPlaceholderColor = VaultColors.inkMuted,
    unfocusedPlaceholderColor = VaultColors.inkMuted,
    focusedLabelColor = VaultColors.inkMuted,
    unfocusedLabelColor = VaultColors.inkMuted,
)

@Composable
fun AuthOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    shapeCorner: Dp = 12.dp,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = authOutlinedFieldColors(),
        shape = RoundedCornerShape(shapeCorner),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = VaultColors.ink,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        ),
    )
}

/** "+852 Hong Kong" / "+886 台灣" → "+852" / "+886". */
fun dialCodeFromLabel(label: String): String {
    val digits = label.filter { it.isDigit() }
    val code = when {
        digits.startsWith("886") -> "886"
        digits.startsWith("852") -> "852"
        digits.startsWith("86") -> "86"
        digits.startsWith("60") -> "60"
        digits.startsWith("65") -> "65"
        digits.startsWith("66") -> "66"
        digits.startsWith("81") -> "81"
        else -> digits.take(3).ifEmpty { "886" }
    }
    return "+$code"
}
