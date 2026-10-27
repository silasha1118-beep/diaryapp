package com.diaryvault.app.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.ui.components.LanguageStrip
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultBackground
import com.diaryvault.app.ui.theme.VaultColors
import kotlinx.coroutines.launch

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
        animationSpec = tween(800),
        label = "welcomeAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (appeared) 0f else 12f,
        animationSpec = tween(800),
        label = "welcomeOffset",
    )

    LaunchedEffect(Unit) { appeared = true }

    VaultBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 28.dp),
        ) {
            LanguageStrip(
                selected = language,
                onSelect = viewModel::setLanguage,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = localizedString(R.string.brand_name),
                            style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier
                                .alpha(alpha)
                                .graphicsLayer { translationY = offsetY },
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = localizedString(R.string.onboarding_welcome_headline),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = localizedString(R.string.onboarding_welcome_body),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    1 -> Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            localizedString(R.string.onboarding_privacy_title),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        listOf(
                            R.string.onboarding_privacy_point1,
                            R.string.onboarding_privacy_point2,
                            R.string.onboarding_privacy_point3,
                        ).forEach { id ->
                            Text(
                                "· " + localizedString(id),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            localizedString(R.string.onboarding_privacy_residency),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    else -> Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            localizedString(R.string.onboarding_name_title),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            localizedString(R.string.onboarding_name_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(20.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(localizedString(R.string.onboarding_name_placeholder)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VaultColors.seal,
                                unfocusedBorderColor = VaultColors.line,
                                focusedTextColor = VaultColors.ink,
                                cursorColor = VaultColors.seal,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 24.dp)) {
                if (pagerState.currentPage < 2) {
                    Button(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultColors.seal),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(localizedString(R.string.common_continue))
                    }
                } else {
                    Button(
                        onClick = { viewModel.completeOnboarding(name) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultColors.seal),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(localizedString(R.string.onboarding_enter))
                    }
                }
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(localizedString(R.string.common_back), color = VaultColors.inkMuted)
                    }
                }
            }
        }
    }
}
