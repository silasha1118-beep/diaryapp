package com.diaryvault.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diaryvault.app.model.AppLanguage
import com.diaryvault.app.ui.theme.VaultColors
import com.diaryvault.app.ui.theme.VaultSerifFamily

/**
 * Quiet language chooser for onboarding — plain text labels, not pill chips.
 * Selected = ink + medium weight; unselected = muted. Separators are hairline dots.
 * Uses [VaultSerifFamily] so zh-Hans matches zh-Hant / EN (no SC system-sans fallback).
 */
@Composable
fun LanguageStrip(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLanguage.entries.forEachIndexed { index, lang ->
            if (index > 0) {
                Text(
                    "·",
                    color = VaultColors.line,
                    fontSize = 13.sp,
                    fontFamily = VaultSerifFamily,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            val selectedNow = lang == selected
            Text(
                text = localizedString(lang.shortLabelRes),
                fontFamily = VaultSerifFamily,
                fontWeight = if (selectedNow) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
                color = if (selectedNow) VaultColors.ink else VaultColors.inkMuted,
                modifier = Modifier
                    .semantics {
                        role = Role.Tab
                        this.selected = selectedNow
                    }
                    .clickable { onSelect(lang) }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
            )
        }
    }
}
