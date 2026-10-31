package com.diaryvault.app.ui.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.model.DiaryEntry
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultColors
import java.text.DateFormat
import java.util.Date

@Composable
fun DiaryNavHost(viewModel: VaultViewModel, modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "list", modifier = modifier) {
        composable("list") {
            DiaryListScreen(
                viewModel = viewModel,
                onOpen = { nav.navigate("detail/$it") },
                onCreate = { nav.navigate("compose") },
            )
        }
        composable("compose") {
            DiaryComposerScreen(
                viewModel = viewModel,
                onDone = { nav.popBackStack() },
                onCancel = { nav.popBackStack() },
            )
        }
        composable(
            "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DiaryDetailScreen(
                viewModel = viewModel,
                entryId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("edit/$id") },
            )
        }
        composable(
            "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DiaryComposerScreen(
                viewModel = viewModel,
                existing = viewModel.entry(id),
                onDone = { nav.popBackStack() },
                onCancel = { nav.popBackStack() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryListScreen(
    viewModel: VaultViewModel,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val name by viewModel.displayName.collectAsState()
    val subtitle = if (name.isBlank()) {
        localizedString(R.string.diary_subtitle)
    } else {
        localizedString(R.string.diary_subtitle_named, name)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(localizedString(R.string.brand_name), style = MaterialTheme.typography.titleLarge)
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                containerColor = VaultColors.seal,
                contentColor = VaultColors.paper,
            ) {
                Icon(Icons.Default.Add, contentDescription = localizedString(R.string.diary_new))
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(localizedString(R.string.diary_empty_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(localizedString(R.string.diary_empty_body), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(entries, key = { it.id }) { item ->
                    DiaryRow(item, onClick = { onOpen(item.id) })
                    HorizontalDivider(color = VaultColors.line.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun DiaryRow(entry: DiaryEntry, onClick: () -> Unit) {
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.createdAt))
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = entry.title.ifBlank { date },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            entry.moodTag?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = VaultColors.seal)
            }
        }
        if (entry.title.isNotBlank()) {
            Text(date, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.preview.ifBlank { localizedString(R.string.diary_empty_body_placeholder) },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryDetailScreen(
    viewModel: VaultViewModel,
    entryId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val entry = entries.firstOrNull { it.id == entryId }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(localizedString(R.string.brand_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = localizedString(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = onEdit) {
                        Text(localizedString(R.string.common_edit), color = VaultColors.seal)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        if (entry == null) {
            Text(
                localizedString(R.string.diary_missing),
                modifier = Modifier.padding(padding).padding(24.dp),
            )
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .fillMaxSize(),
            ) {
                val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(entry.createdAt))
                Text(entry.title.ifBlank { date }, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(date, style = MaterialTheme.typography.bodyMedium)
                entry.moodTag?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = VaultColors.seal, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    entry.body.ifBlank { localizedString(R.string.diary_empty_body_placeholder) },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(24.dp))
                Text(localizedString(R.string.diary_detail_owned), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryComposerScreen(
    viewModel: VaultViewModel,
    existing: DiaryEntry? = null,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var body by remember { mutableStateOf(existing?.body.orEmpty()) }
    var mood by remember { mutableStateOf(existing?.moodTag.orEmpty()) }
    var softRemaining by remember { mutableStateOf<Int?>(null) }
    var softLimit by remember { mutableStateOf<Int?>(null) }
    var hardTitle by remember { mutableStateOf<String?>(null) }
    var hardBenefit by remember { mutableStateOf<String?>(null) }
    var hardMeta by remember { mutableStateOf<String?>(null) }
    var hardShowUpgrade by remember { mutableStateOf(false) }
    var hardUpgradePro by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VaultColors.seal,
        unfocusedBorderColor = VaultColors.line,
        focusedTextColor = VaultColors.ink,
        cursorColor = VaultColors.seal,
    )

    fun trySave(suppressSoft: Boolean = false) {
        if (existing != null) {
            viewModel.updateEntry(
                existing.copy(title = title.trim(), body = body.trim(), moodTag = mood.trim().ifBlank { null }),
            )
            onDone()
            return
        }
        when (
            val result = viewModel.addEntry(title, body, mood, suppressSoftPaywall = suppressSoft)
        ) {
            is VaultViewModel.AddEntryResult.Saved -> onDone()
            is VaultViewModel.AddEntryResult.SoftPaywall -> {
                softRemaining = (result.limit - result.used).coerceAtLeast(0)
                softLimit = result.limit
                hardTitle = null
                hardBenefit = null
                hardMeta = null
            }
            is VaultViewModel.AddEntryResult.Blocked -> {
                softRemaining = null
                softLimit = null
                when {
                    result.isAuthorPro -> {
                        hardTitle = viewModel.string(R.string.diary_quota_hard_title_pro)
                        hardBenefit = viewModel.string(R.string.diary_quota_hard_benefit_pro)
                        hardShowUpgrade = false
                        hardUpgradePro = false
                    }
                    result.isAuthorPlus -> {
                        hardTitle = viewModel.string(R.string.diary_quota_hard_title_plus)
                        hardBenefit = viewModel.string(R.string.diary_quota_hard_benefit_plus)
                        hardShowUpgrade = true
                        hardUpgradePro = true
                    }
                    else -> {
                        hardTitle = viewModel.string(R.string.diary_quota_hard_title_free)
                        hardBenefit = viewModel.string(R.string.diary_quota_hard_benefit_free)
                        hardShowUpgrade = true
                        hardUpgradePro = false
                    }
                }
                hardMeta = viewModel.string(R.string.diary_quota_hard_meta, result.limit)
            }
        }
    }

    val softPending = softRemaining != null && softLimit != null
    if (softPending) {
        val remaining = softRemaining ?: 0
        val limit = softLimit ?: 0
        AlertDialog(
            onDismissRequest = {
                softRemaining = null
                softLimit = null
            },
            title = { Text(localizedString(R.string.diary_quota_soft_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedString(R.string.diary_quota_soft_benefit))
                    Text(
                        viewModel.string(R.string.diary_quota_soft_meta, remaining, limit),
                        color = VaultColors.inkMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            // Primary = upgrade (Play Billing not wired — dismiss keeps composer open).
            confirmButton = {
                TextButton(
                    onClick = {
                        softRemaining = null
                        softLimit = null
                    },
                ) {
                    Text(localizedString(R.string.diary_quota_upgrade), color = VaultColors.seal)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        softRemaining = null
                        softLimit = null
                        trySave(suppressSoft = true)
                    },
                ) {
                    Text(localizedString(R.string.diary_quota_soft_continue))
                }
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(localizedString(if (existing == null) R.string.diary_new else R.string.common_edit)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = localizedString(R.string.common_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = { trySave() }) {
                        Text(localizedString(R.string.common_save), color = VaultColors.seal)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(localizedString(R.string.diary_field_title)) },
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = mood,
                onValueChange = { mood = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(localizedString(R.string.diary_field_mood)) },
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text(localizedString(R.string.diary_field_body)) },
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
            )
            if (!softPending) {
                hardTitle?.let { titleText ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            titleText,
                            color = VaultColors.ink,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        hardBenefit?.let { benefit ->
                            Text(
                                benefit,
                                color = VaultColors.inkMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        hardMeta?.let { meta ->
                            Text(
                                meta,
                                color = VaultColors.inkMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (hardShowUpgrade) {
                            Text(
                                localizedString(
                                    if (hardUpgradePro) {
                                        R.string.diary_quota_upgrade_pro
                                    } else {
                                        R.string.diary_quota_upgrade
                                    },
                                ),
                                color = VaultColors.seal,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
