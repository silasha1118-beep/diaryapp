package com.silasha.diaryvault.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.silasha.diaryvault.model.AppLanguage
import com.silasha.diaryvault.ui.theme.VaultColors
import com.silasha.diaryvault.ui.theme.VaultSerifFamily

/**
 * Quiet language chooser for onboarding — plain text labels, not pill chips.
 * All five supported locales; scrolls horizontally on narrow widths (Redmi 9C)
 * so nothing clips. Selected = ink + medium weight; unselected = muted.
 */
@Composable
fun LanguageStrip(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLanguage.entries.forEachIndexed { index, lang ->
            if (index > 0) {
                Text(
                    "·",
                    color = VaultColors.line,
                    fontSize = 12.sp,
                    fontFamily = VaultSerifFamily,
                    modifier = Modifier.padding(horizontal = 5.dp),
                )
            }
            val selectedNow = lang == selected
            Text(
                text = localizedString(lang.shortLabelRes),
                fontFamily = VaultSerifFamily,
                fontWeight = if (selectedNow) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp,
                color = if (selectedNow) VaultColors.ink else VaultColors.inkMuted,
                modifier = Modifier
                    .semantics {
                        role = Role.Tab
                        this.selected = selectedNow
                    }
                    .clickable { onSelect(lang) }
                    .padding(vertical = 6.dp, horizontal = 1.dp),
            )
        }
    }
}
