package com.diaryvault.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.model.DiaryEntry
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultColors
import java.text.DateFormat
import java.util.Date

private val CardBg = Color.White
private val CardRadius = 14.dp
private val DividerColor = Color(0xFFE8E2D8)

/**
 * Soft-deleted entries from the last 30 days — restore here (same APIs as the former
 * inline Settings list). Empty state when none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recentlyDeleted by viewModel.recentlyDeletedEntries.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        localizedString(R.string.settings_vault_recently_deleted),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = localizedString(R.string.common_back),
                            tint = VaultColors.ink,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        if (recentlyDeleted.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    localizedString(R.string.settings_vault_restore_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = VaultColors.inkMuted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            ) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(CardBg, RoundedCornerShape(CardRadius)),
                    ) {
                        recentlyDeleted.forEachIndexed { index, entry ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = DividerColor,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                            RecentlyDeletedRow(
                                entry = entry,
                                onRestore = { viewModel.restoreEntry(entry.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecentlyDeletedRow(
    entry: DiaryEntry,
    onRestore: () -> Unit,
) {
    val deletedAt = entry.deletedAt ?: entry.updatedAt
    val whenText = DateFormat
        .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(deletedAt))
    val title = entry.title.ifBlank {
        entry.preview.ifBlank { localizedString(R.string.diary_empty_body_placeholder) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = VaultColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                whenText,
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            localizedString(R.string.settings_vault_restore),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = VaultColors.seal,
            modifier = Modifier
                .clickable(onClick = onRestore)
                .padding(vertical = 4.dp),
        )
    }
}
