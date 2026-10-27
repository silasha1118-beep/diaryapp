package com.diaryvault.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.diaryvault.app.model.AppLanguage
import com.diaryvault.app.ui.theme.VaultColors
// localizedString resolves in-app language overrides

@Composable
fun LanguageStrip(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLanguage.entries.forEach { lang ->
            val selectedNow = lang == selected
            val bg by animateColorAsState(
                if (selectedNow) VaultColors.ink else VaultColors.paper.copy(alpha = 0f),
                label = "langBg",
            )
            val fg by animateColorAsState(
                if (selectedNow) VaultColors.paper else VaultColors.inkMuted,
                label = "langFg",
            )
            Surface(
                color = bg,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (!selectedNow) Modifier.border(1.dp, VaultColors.line, RoundedCornerShape(50))
                        else Modifier,
                    )
                    .clickable { onSelect(lang) },
            ) {
                Text(
                    text = localizedString(lang.shortLabelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
