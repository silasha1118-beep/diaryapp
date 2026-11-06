package com.diaryvault.app.ui.mirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.billing.BillingConfig
import com.diaryvault.app.model.MirrorMessage
import com.diaryvault.app.model.MirrorRole
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.paywall.UpgradeCheckoutSheet
import com.diaryvault.app.ui.theme.VaultColors
import kotlinx.coroutines.flow.first

/**
 * Mirror tab — opens straight into chat (iOS `MirrorChatView`).
 * Live xAI Grok when `XAI_API_KEY` / `DIARY_AI_API_KEY` is set at build time.
 */
@Composable
fun MirrorTabScreen(viewModel: VaultViewModel, modifier: Modifier = Modifier) {
    MirrorChatPane(viewModel = viewModel, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MirrorChatPane(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.mirrorMessages.collectAsState()
    val thinking by viewModel.mirrorThinking.collectAsState()
    val error by viewModel.mirrorError.collectAsState()
    val quotaTick by viewModel.mirrorQuotaTick.collectAsState()
    val billingActive by viewModel.billingActive.collectAsState()
    val billingTier by viewModel.billingTier.collectAsState()
    var draft by remember { mutableStateOf("") }
    var showUpgrade by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val canSend = draft.isNotBlank() && !thinking

    val exhausted = remember(quotaTick, billingActive, billingTier) { viewModel.mirrorQuotaExhausted() }
    val remaining = remember(quotaTick, billingActive, billingTier) { viewModel.mirrorQuotaRemaining() }
    val limit = remember(quotaTick, billingActive, billingTier) { viewModel.mirrorQuotaLimit() }
    val cloudReady = viewModel.useCloudClient
    val showUpgradeCta = exhausted && billingTier != BillingConfig.Tier.AUTHOR_PRO
    val preferredUpgradeTier =
        if (billingTier == BillingConfig.Tier.AUTHOR_PLUS) {
            BillingConfig.Tier.AUTHOR_PRO
        } else {
            BillingConfig.Tier.AUTHOR_PLUS
        }

    // Pin the latest row just above the composer (iOS ScrollViewReader anchor: .bottom).
    // animateScrollToItem alone puts the row at the *top* of the viewport and leaves a
    // large blank under the last bubble after the assistant replies.
    val lastMessageId = messages.lastOrNull()?.id
    LaunchedEffect(messages.size, lastMessageId, thinking, error) {
        listState.scrollLatestToBottom()
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        localizedString(R.string.mirror_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = VaultColors.ink,
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // Quota / mode strip (iOS MirrorChatView parity)
            MirrorQuotaStrip(
                cloudReady = cloudReady,
                exhausted = exhausted,
                remaining = remaining,
                limit = limit,
                isFree = !billingActive,
                isPro = billingTier == BillingConfig.Tier.AUTHOR_PRO,
                showUpgrade = showUpgradeCta,
                onUpgrade = { showUpgrade = true },
            )

            if (showUpgrade) {
                UpgradeCheckoutSheet(
                    viewModel = viewModel,
                    preferredTier = preferredUpgradeTier,
                    onDismiss = { showUpgrade = false },
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    Text(
                        localizedString(R.string.mirror_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultColors.inkMuted,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                items(messages, key = { it.id }) { msg ->
                    MirrorBubble(msg)
                }
                if (thinking) {
                    item {
                        Text(
                            localizedString(R.string.mirror_thinking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = VaultColors.inkMuted,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
                if (error != null) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    VaultColors.seal.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                error ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VaultColors.seal,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                            TextButton(
                                onClick = { viewModel.clearMirrorError() },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    localizedString(R.string.mirror_error_dismiss),
                                    color = VaultColors.seal,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(VaultColors.paperDeep.copy(alpha = 0.85f))
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        if (error != null) viewModel.clearMirrorError()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .background(VaultColors.paper, RoundedCornerShape(8.dp))
                        .border(1.dp, VaultColors.line.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 16.sp,
                        color = VaultColors.ink,
                        lineHeight = 22.sp,
                    ),
                    cursorBrush = SolidColor(VaultColors.seal),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    localizedString(R.string.mirror_placeholder),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 16.sp,
                                        color = VaultColors.inkMuted,
                                    ),
                                )
                            }
                            inner()
                        }
                    },
                )
                IconButton(
                    onClick = {
                        val text = draft
                        draft = ""
                        viewModel.sendMirror(text)
                    },
                    enabled = canSend && !exhausted,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (canSend && !exhausted) VaultColors.seal else VaultColors.line,
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = localizedString(R.string.mirror_send),
                        tint = if (canSend && !exhausted) VaultColors.paper else VaultColors.inkMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MirrorQuotaStrip(
    cloudReady: Boolean,
    exhausted: Boolean,
    remaining: Int?,
    limit: Int,
    isFree: Boolean,
    isPro: Boolean,
    showUpgrade: Boolean,
    onUpgrade: () -> Unit,
) {
    val status = when {
        !cloudReady -> localizedString(R.string.mirror_mode_needs_key)
        remaining == null -> localizedString(R.string.mirror_quota_unlimited)
        exhausted && isFree -> localizedString(R.string.mirror_quota_exhausted_free, limit)
        exhausted && isPro -> localizedString(R.string.mirror_quota_exhausted_pro, limit)
        exhausted && !isFree -> localizedString(R.string.mirror_quota_exhausted_plus, limit)
        isFree -> localizedString(R.string.mirror_quota_remaining_lifetime, remaining, limit)
        else -> localizedString(R.string.mirror_quota_remaining_daily, remaining, limit)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (exhausted || !cloudReady) {
                    VaultColors.seal.copy(alpha = 0.08f)
                } else {
                    VaultColors.paperDeep.copy(alpha = 0.45f)
                },
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 12.sp,
            color = if (exhausted || !cloudReady) VaultColors.seal else VaultColors.inkMuted,
        )
        if (showUpgrade) {
            Text(
                text = localizedString(
                    if (isFree) R.string.diary_quota_upgrade else R.string.diary_quota_upgrade_pro,
                ),
                style = MaterialTheme.typography.labelLarge,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = VaultColors.seal,
                modifier = Modifier.clickable(onClick = onUpgrade),
            )
        }
    }
}

/**
 * Scroll so the last LazyColumn item’s bottom sits on the viewport bottom (flush with
 * the composer). Short threads that don’t fill the viewport stay top-aligned — same
 * behavior as iOS `scrollTo(..., anchor: .bottom)`.
 */
private suspend fun LazyListState.scrollLatestToBottom() {
    // Wait until composition has produced at least the intro row.
    snapshotFlow { layoutInfo.totalItemsCount }.first { it > 0 }
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    // Bring the last row into view (may leave blank under it when the row is short).
    animateScrollToItem(lastIndex)
    // After layout, nudge so the row bottom meets the viewport bottom when possible.
    snapshotFlow {
        layoutInfo.visibleItemsInfo.any { it.index == lastIndex } ||
            layoutInfo.visibleItemsInfo.isNotEmpty()
    }.first { it }
    val info = layoutInfo
    val item = info.visibleItemsInfo.lastOrNull { it.index == lastIndex }
        ?: info.visibleItemsInfo.lastOrNull()
        ?: return
    val viewportBottom = info.viewportEndOffset - info.afterContentPadding
    val delta = (item.offset + item.size) - viewportBottom
    if (kotlin.math.abs(delta) > 1) {
        animateScrollBy(delta.toFloat())
    }
}

@Composable
private fun MirrorBubble(message: MirrorMessage) {
    val isUser = message.role == MirrorRole.User
    val bubbleAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = bubbleAlignment,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    color = if (isUser) VaultColors.userBubble else VaultColors.mirrorBubble,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(16.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = VaultColors.ink,
            )
            if (!isUser && message.citedEntryIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = localizedString(R.string.mirror_cited),
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 12.sp,
                    color = VaultColors.inkMuted,
                )
            }
        }
    }
}
