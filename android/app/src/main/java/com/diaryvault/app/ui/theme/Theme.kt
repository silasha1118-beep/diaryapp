package com.diaryvault.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diaryvault.app.model.AppLanguage
import java.util.Locale

/** Visual language: personal vault — warm paper, ink, soft depth. Not generic notes chrome. */
object VaultColors {
    val paper = Color(0xFFF5F1E8)
    val paperDeep = Color(0xFFE6DED0)
    val ink = Color(0xFF29241F)
    val inkMuted = Color(0xFF665C52)
    val seal = Color(0xFF8C382E)
    val line = Color(0xFFC7BDAE)
    val mirrorBubble = Color(0xFFEDE6D9)
    val userBubble = Color(0xFFE0D1BC)
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.English }

@Composable
fun DiaryVaultTheme(
    language: AppLanguage = AppLanguage.English,
    content: @Composable () -> Unit,
) {
    val scheme = lightColorScheme(
        primary = VaultColors.seal,
        onPrimary = VaultColors.paper,
        secondary = VaultColors.inkMuted,
        background = VaultColors.paper,
        onBackground = VaultColors.ink,
        surface = VaultColors.paper,
        onSurface = VaultColors.ink,
        outline = VaultColors.line,
    )

    val serif = FontFamily.Serif
    val typography = Typography(
        displayLarge = TextStyle(
            fontFamily = serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 40.sp,
            color = VaultColors.ink,
        ),
        headlineMedium = TextStyle(
            fontFamily = serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            color = VaultColors.ink,
        ),
        titleLarge = TextStyle(
            fontFamily = serif,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            color = VaultColors.ink,
        ),
        bodyLarge = TextStyle(
            fontFamily = serif,
            fontWeight = FontWeight.Normal,
            fontSize = 17.sp,
            lineHeight = 26.sp,
            color = VaultColors.ink,
        ),
        bodyMedium = TextStyle(
            fontFamily = serif,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = VaultColors.inkMuted,
        ),
        labelLarge = TextStyle(
            fontFamily = serif,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = VaultColors.ink,
        ),
    )

    CompositionLocalProvider(LocalAppLanguage provides language) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            content = content,
        )
    }
}

@Composable
fun VaultBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF7F3E8),
                        Color(0xFFEBE4D6),
                        Color(0xFFE0DBCC),
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
    ) {
        Box(
            Modifier
                .size(280.dp)
                .offset((-60).dp, (-20).dp)
                .blur(60.dp)
                .background(VaultColors.paperDeep.copy(alpha = 0.55f), CircleShape),
        )
        Box(
            Modifier
                .size(220.dp)
                .offset(220.dp, 480.dp)
                .blur(50.dp)
                .background(VaultColors.seal.copy(alpha = 0.07f), CircleShape),
        )
        content()
    }
}

fun localeFor(language: AppLanguage): Locale = when (language) {
    AppLanguage.ZhHant -> Locale.TRADITIONAL_CHINESE
    AppLanguage.ZhHans -> Locale.SIMPLIFIED_CHINESE
    AppLanguage.English -> Locale.ENGLISH
}
