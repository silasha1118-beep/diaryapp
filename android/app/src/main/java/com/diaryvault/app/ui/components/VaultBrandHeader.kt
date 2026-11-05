package com.diaryvault.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diaryvault.app.ui.theme.VaultColors

/** Brand-first vault title — large serif name, seal rule, quiet subtitle (iOS VaultBrandHeader). */
@Composable
fun VaultBrandHeader(
    title: String,
    subtitle: String? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(650),
        label = "brandAlpha",
    )
    val sealScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.35f,
        animationSpec = tween(650),
        label = "sealScale",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (appeared) 0f else 8f,
        animationSpec = tween(650),
        label = "brandOffset",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = offsetY }
            .alpha(alpha),
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 27.sp else 34.sp,
            color = VaultColors.ink,
            maxLines = 1,
        )
        Spacer(Modifier.height(if (compact) 10.dp else 12.dp))
        Box(
            Modifier
                .width(if (compact) 22.dp else 32.dp)
                .height(if (compact) 2.dp else 2.5.dp)
                .graphicsLayer {
                    scaleX = sealScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }
                .background(VaultColors.seal.copy(alpha = 0.9f), RoundedCornerShape(2.dp)),
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(if (compact) 12.dp else 14.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.inkMuted,
            )
        }
    }
}
