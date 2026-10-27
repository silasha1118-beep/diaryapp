package com.diaryvault.app.ui.mirror

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.model.MirrorMessage
import com.diaryvault.app.model.MirrorRole
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorChatScreen(viewModel: VaultViewModel, rootModifier: Modifier = Modifier) {
    val messages by viewModel.mirrorMessages.collectAsState()
    val thinking by viewModel.mirrorThinking.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, thinking) {
        val last = messages.size + if (thinking) 1 else 0
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    Scaffold(
        modifier = rootModifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(localizedString(R.string.mirror_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = localizedString(
                                if (viewModel.useCloudClient) R.string.mirror_mode_cloud
                                else R.string.mirror_mode_mock,
                            ) + " · " + localizedString(R.string.mirror_style_hint),
                            style = MaterialTheme.typography.bodyMedium,
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        localizedString(R.string.mirror_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
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
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(localizedString(R.string.mirror_placeholder)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultColors.seal,
                        unfocusedBorderColor = VaultColors.line,
                        cursorColor = VaultColors.seal,
                    ),
                    shape = RoundedCornerShape(14.dp),
                )
                Button(
                    onClick = {
                        val text = draft
                        draft = ""
                        viewModel.sendMirror(text)
                    },
                    enabled = draft.isNotBlank() && !thinking,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.seal),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(localizedString(R.string.mirror_send))
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
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = VaultColors.ink,
            )
            if (!isUser && message.citedEntryIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = localizedString(R.string.mirror_cited),
                    style = MaterialTheme.typography.labelLarge,
                    color = VaultColors.seal,
                )
            }
        }
    }
}
