package com.diaryvault.app.ui.mirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.diaryvault.app.model.MirrorMessage
import com.diaryvault.app.model.MirrorRole
import com.diaryvault.app.ui.components.VaultBrandHeader
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultColors

/**
 * Mirror tab: home (今日一問) ↔ in-tab chat with mock reply — iOS MirrorChatView chrome.
 */
@Composable
fun MirrorTabScreen(viewModel: VaultViewModel, modifier: Modifier = Modifier) {
    var inChat by rememberSaveable { mutableStateOf(false) }
    if (inChat) {
        MirrorChatPane(
            viewModel = viewModel,
            modifier = modifier,
            onBack = { inChat = false },
        )
    } else {
        MirrorHomePane(
            modifier = modifier,
            onStartChat = { inChat = true },
        )
    }
}

@Composable
private fun MirrorHomePane(
    modifier: Modifier = Modifier,
    onStartChat: () -> Unit,
) {
    // Keep home hierarchy Silas confirmed close: 鏡子 + seal + intro, 今日一問, 開始對話
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VaultBrandHeader(
            title = localizedString(R.string.mirror_title),
            subtitle = localizedString(R.string.mirror_intro),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                localizedString(R.string.mirror_today_label),
                style = MaterialTheme.typography.labelLarge,
                color = VaultColors.seal,
            )
            Text(
                localizedString(R.string.mirror_today_question),
                style = MaterialTheme.typography.titleLarge,
                color = VaultColors.ink,
            )
        }
        Button(
            onClick = onStartChat,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultColors.seal,
                contentColor = VaultColors.paper,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(localizedString(R.string.mirror_start_chat))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MirrorChatPane(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val messages by viewModel.mirrorMessages.collectAsState()
    val thinking by viewModel.mirrorThinking.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val canSend = draft.isNotBlank() && !thinking

    LaunchedEffect(messages.size, thinking) {
        val last = messages.size + if (thinking) 1 else 0
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            localizedString(R.string.mirror_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = VaultColors.ink,
                        )
                        // Seal-tone subtitle — iOS style hint under title
                        Text(
                            text = localizedString(R.string.mirror_style_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = VaultColors.seal.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = localizedString(R.string.common_back),
                            tint = VaultColors.inkMuted,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    // System intro — no bubble (iOS MirrorRole.system)
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
            }

            // Composer — paperDeep strip, cream field, circular send
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
                    onValueChange = { draft = it },
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
                    enabled = canSend,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (canSend) VaultColors.seal else VaultColors.line,
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = localizedString(R.string.mirror_send),
                        tint = if (canSend) VaultColors.paper else VaultColors.inkMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
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
