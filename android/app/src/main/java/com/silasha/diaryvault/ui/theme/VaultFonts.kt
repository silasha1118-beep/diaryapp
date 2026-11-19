package com.silasha.diaryvault.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.silasha.diaryvault.R

/**
 * Bundled Noto Serif SC (subset) so zh-Hans does not fall back to a thinner
 * system sans while zh-Hant / EN resolve [FontFamily.Serif] to a display serif.
 * Used on welcome/onboarding (language strip, body, CTA) for one typeface.
 */
val VaultSerifFamily: FontFamily = FontFamily(
    Font(R.font.noto_serif_sc_regular, FontWeight.Normal),
    Font(R.font.noto_serif_sc_medium, FontWeight.Medium),
    Font(R.font.noto_serif_sc_semibold, FontWeight.SemiBold),
)
