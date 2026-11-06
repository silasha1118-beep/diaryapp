package com.diaryvault.app.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.ui.components.LanguageStrip
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultBackground
import com.diaryvault.app.ui.theme.VaultColors
import com.diaryvault.app.ui.theme.VaultSerifFamily
import kotlinx.coroutines.launch

/**
 * Welcome / onboarding — balanced vertical rhythm for Redmi 9C-class heights:
 * five-language strip, brand-first copy with breathing room under status bar,
 * seal-filled Continue with explicit paper label (theme typography ink must not
 * paint black-on-black). CTA stays above system nav.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(viewModel: VaultViewModel) {
    val language by viewModel.language.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var appeared by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(650),
        label = "welcomeAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (appeared) 0f else 10f,
        animationSpec = tween(650),
        label = "welcomeOffset",
    )

    LaunchedEffect(Unit) { appeared = true }
    // Redmi 9C ~720×1600 often reports ~800dp height — treat as compact.
    val compact = LocalConfiguration.current.screenHeightDp < 840
    val pagePadH = if (compact) 22.dp else 28.dp
    val brandSize = if (compact) 28.sp else 36.sp
    val headlineSize = if (compact) 17.sp else 20.sp

    VaultBackground(atmosphere = false) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.ime),
        ) {
            LanguageStrip(
                selected = language,
                onSelect = viewModel::setLanguage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = pagePadH,
                        end = pagePadH,
                        top = if (compact) 14.dp else 18.dp,
                        bottom = if (compact) 4.dp else 8.dp,
                    ),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> WelcomePage(
                        brandSize = brandSize,
                        headlineSize = headlineSize,
                        compact = compact,
                        horizontalPad = pagePadH,
                        contentAlpha = alpha,
                        contentOffsetY = offsetY,
                    )
                    1 -> PrivacyPage(compact = compact, horizontalPad = pagePadH)
                    else -> NamePage(
                        name = name,
                        onNameChange = { name = it },
                        compact = compact,
                        horizontalPad = pagePadH,
                    )
                }
            }

            // CTA dock — seal fill + explicit paper label (avoids theme ink on Text).
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = pagePadH)
                    .padding(
                        top = if (compact) 8.dp else 12.dp,
                        bottom = if (compact) 14.dp else 18.dp,
                    ),
            ) {
                Button(
                    onClick = {
                        if (pagerState.currentPage < 2) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            viewModel.completeOnboarding(name)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 52.dp else 56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultColors.seal,
                        contentColor = VaultColors.paper,
                        disabledContainerColor = VaultColors.seal.copy(alpha = 0.45f),
                        disabledContentColor = VaultColors.paper,
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 1.dp,
                        pressedElevation = 0.dp,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        localizedString(
                            if (pagerState.currentPage < 2) R.string.common_continue
                            else R.string.onboarding_enter,
                        ),
                        fontFamily = VaultSerifFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (compact) 17.sp else 18.sp,
                        // Explicit: Material typography embeds ink and would hide on dark fills.
                        color = VaultColors.paper,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = if (compact) 4.dp else 6.dp)
                        .height(40.dp),
                ) {
                    if (pagerState.currentPage > 0) {
                        TextButton(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            },
                            modifier = Modifier.align(Alignment.CenterStart),
                        ) {
                            Text(
                                localizedString(R.string.common_back),
                                color = VaultColors.inkMuted,
                                fontFamily = VaultSerifFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    PageDots(
                        count = 3,
                        selected = pagerState.currentPage,
                        compact = compact,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(
    brandSize: TextUnit,
    headlineSize: TextUnit,
    compact: Boolean,
    horizontalPad: Dp,
    contentAlpha: Float,
    contentOffsetY: Float,
) {
    // Balanced rhythm: soft top breath + content block + flexible mid spacer (not top-jammed).
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPad),
    ) {
        Spacer(Modifier.height(if (compact) 28.dp else 40.dp))
        Text(
            text = localizedString(R.string.brand_name),
            fontFamily = VaultSerifFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = brandSize,
            color = VaultColors.ink,
            lineHeight = brandSize * 1.15f,
            modifier = Modifier
                .alpha(contentAlpha)
                .graphicsLayer { translationY = contentOffsetY },
        )
        Spacer(Modifier.height(if (compact) 12.dp else 14.dp))
        Box(
            Modifier
                .width(if (compact) 28.dp else 36.dp)
                .height(2.5.dp)
                .alpha(contentAlpha)
                .background(VaultColors.seal.copy(alpha = 0.9f), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.height(if (compact) 16.dp else 22.dp))
        Text(
            text = localizedString(R.string.onboarding_welcome_headline),
            fontFamily = VaultSerifFamily,
            fontWeight = FontWeight.Medium,
            fontSize = headlineSize,
            lineHeight = headlineSize * 1.35f,
            color = VaultColors.ink,
            modifier = Modifier.alpha(contentAlpha),
        )
        Spacer(Modifier.height(if (compact) 12.dp else 14.dp))
        Text(
            text = localizedString(R.string.onboarding_welcome_body),
            fontFamily = VaultSerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = if (compact) 15.sp else 16.sp,
            lineHeight = if (compact) 24.sp else 26.sp,
            color = VaultColors.inkMuted,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentAlpha),
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PrivacyPage(compact: Boolean, horizontalPad: Dp) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPad)
            .padding(top = if (compact) 28.dp else 40.dp, bottom = 8.dp),
    ) {
        Text(
            localizedString(R.string.onboarding_privacy_title),
            fontFamily = VaultSerifFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 24.sp else 28.sp,
            color = VaultColors.ink,
        )
        Spacer(Modifier.height(if (compact) 16.dp else 20.dp))
        listOf(
            R.string.onboarding_privacy_point1,
            R.string.onboarding_privacy_point2,
            R.string.onboarding_privacy_point3,
        ).forEach { id ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (compact) 14.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "·",
                    color = VaultColors.seal,
                    fontSize = 18.sp,
                    fontFamily = VaultSerifFamily,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    localizedString(id),
                    fontFamily = VaultSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = if (compact) 15.sp else 16.sp,
                    lineHeight = if (compact) 24.sp else 26.sp,
                    color = VaultColors.ink,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NamePage(
    name: String,
    onNameChange: (String) -> Unit,
    compact: Boolean,
    horizontalPad: Dp,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPad)
            .padding(top = if (compact) 28.dp else 40.dp, bottom = 8.dp),
    ) {
        Text(
            localizedString(R.string.onboarding_name_title),
            fontFamily = VaultSerifFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 24.sp else 28.sp,
            color = VaultColors.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            localizedString(R.string.onboarding_name_body),
            fontFamily = VaultSerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = if (compact) 14.sp else 15.sp,
            lineHeight = if (compact) 21.sp else 22.sp,
            color = VaultColors.inkMuted,
        )
        Spacer(Modifier.height(if (compact) 18.dp else 22.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                fontFamily = VaultSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = VaultColors.ink,
            ),
            placeholder = {
                Text(
                    localizedString(R.string.onboarding_name_placeholder),
                    fontFamily = VaultSerifFamily,
                    color = VaultColors.inkMuted,
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VaultColors.seal,
                unfocusedBorderColor = VaultColors.line,
                focusedTextColor = VaultColors.ink,
                unfocusedTextColor = VaultColors.ink,
                cursorColor = VaultColors.seal,
                focusedContainerColor = VaultColors.paper.copy(alpha = 0.55f),
                unfocusedContainerColor = VaultColors.paper.copy(alpha = 0.4f),
            ),
            shape = RoundedCornerShape(12.dp),
        )
    }
}

@Composable
private fun PageDots(
    count: Int,
    selected: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == selected
            Box(
                Modifier
                    .size(if (active) 8.dp else 6.dp)
                    .background(
                        color = if (active) VaultColors.seal else VaultColors.line,
                        shape = CircleShape,
                    ),
            )
        }
    }
}
