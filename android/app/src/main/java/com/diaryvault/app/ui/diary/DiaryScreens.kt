package com.diaryvault.app.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.model.DiaryEntry
import com.diaryvault.app.ui.components.VaultBrandHeader
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

    // iOS DiaryListView: trailing toolbar `square.and.pencil` — no bottom FAB.
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = onCreate) {
                        Icon(
                            painter = painterResource(R.drawable.ic_square_and_pencil),
                            contentDescription = localizedString(R.string.diary_new),
                            tint = VaultColors.ink,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                VaultBrandHeader(
                    title = localizedString(R.string.brand_name),
                    subtitle = subtitle,
                )
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        localizedString(R.string.diary_empty_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        localizedString(R.string.diary_empty_body),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onCreate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VaultColors.ink,
                            contentColor = VaultColors.paper,
                        ),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(localizedString(R.string.diary_new))
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    VaultBrandHeader(
                        title = localizedString(R.string.brand_name),
                        subtitle = subtitle,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                }
                items(entries, key = { it.id }) { item ->
                    DiaryRow(item, onClick = { onOpen(item.id) })
                    HorizontalDivider(color = VaultColors.line.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun DiaryRow(entry: DiaryEntry, onClick: () -> Unit) {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(entry.createdAt))
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelLarge,
                color = VaultColors.seal,
            )
            entry.moodTag?.let {
                Text("·", color = VaultColors.inkMuted.copy(alpha = 0.7f))
                Text(it, style = MaterialTheme.typography.labelLarge, color = VaultColors.seal)
            }
        }
        Text(
            text = entry.title.ifBlank { localizedString(R.string.diary_empty_body_placeholder) },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.preview.ifBlank { localizedString(R.string.diary_empty_body_placeholder) },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = VaultColors.inkMuted,
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
    var hardTitle by remember { mutableStateOf<String?>(null) }
    var hardBenefit by remember { mutableStateOf<String?>(null) }
    var hardMeta by remember { mutableStateOf<String?>(null) }
    var hardShowUpgrade by remember { mutableStateOf(false) }
    var hardUpgradePro by remember { mutableStateOf(false) }
    // iOS: canSave when body has text (or attachments — stubs empty here)
    val canSave = body.isNotBlank()

    fun trySave() {
        if (!canSave) return
        if (existing != null) {
            viewModel.updateEntry(
                existing.copy(title = title.trim(), body = body.trim(), moodTag = mood.trim().ifBlank { null }),
            )
            onDone()
            return
        }
        when (val result = viewModel.addEntry(title, body, mood)) {
            is VaultViewModel.AddEntryResult.Saved -> onDone()
            is VaultViewModel.AddEntryResult.Blocked -> {
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

    Scaffold(
        containerColor = VaultColors.paper,
        topBar = {
            // iOS: Cancel · 新日記 · 儲存 (gray when empty)
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        localizedString(if (existing == null) R.string.diary_new else R.string.common_edit),
                        color = VaultColors.ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text(
                            localizedString(R.string.common_cancel),
                            color = VaultColors.inkMuted,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { trySave() },
                        enabled = canSave,
                    ) {
                        Text(
                            localizedString(R.string.common_save),
                            color = if (canSave) VaultColors.seal else VaultColors.inkMuted,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = VaultColors.paper,
                ),
            )
        },
        bottomBar = {
            // iOS composerAttachDock: divider + mic/photo/video + muted duration caption
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = VaultColors.line.copy(alpha = 0.7f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(VaultColors.paper)
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaStubIcon(Icons.Default.Mic, localizedString(R.string.diary_media_voice))
                    MediaStubIcon(Icons.Default.Image, localizedString(R.string.diary_media_photo))
                    MediaStubIcon(Icons.Default.Videocam, localizedString(R.string.diary_media_video))
                    Spacer(Modifier.weight(1f))
                    Text(
                        localizedString(R.string.diary_media_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultColors.inkMuted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        modifier = Modifier.weight(2.5f),
                    )
                }
            }
        },
    ) { padding ->
        // iOS formContent: title → mood → hairline → body (borderless, cream paper)
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ComposerPlainField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = localizedString(R.string.diary_field_title),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        color = VaultColors.ink,
                        lineHeight = 28.sp,
                    ),
                    placeholderColor = VaultColors.inkMuted.copy(alpha = 0.55f),
                    singleLine = true,
                )
                ComposerPlainField(
                    value = mood,
                    onValueChange = { mood = it },
                    placeholder = localizedString(R.string.diary_field_mood),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = VaultColors.inkMuted,
                        lineHeight = 20.sp,
                    ),
                    placeholderColor = VaultColors.inkMuted.copy(alpha = 0.7f),
                    singleLine = true,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = VaultColors.line.copy(alpha = 0.85f),
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                ComposerPlainField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = localizedString(R.string.diary_field_body),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 17.sp,
                        color = VaultColors.ink,
                        lineHeight = 26.sp,
                    ),
                    placeholderColor = VaultColors.inkMuted.copy(alpha = 0.55f),
                    singleLine = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            hardTitle?.let { titleText ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        titleText,
                        color = VaultColors.ink,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    hardBenefit?.let { benefit ->
                        Text(
                            benefit,
                            color = VaultColors.inkMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    hardMeta?.let { meta ->
                        Text(
                            meta,
                            color = VaultColors.inkMuted,
                            style = MaterialTheme.typography.labelLarge,
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

@Composable
private fun ComposerPlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    placeholderColor: Color,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = textStyle,
        cursorBrush = SolidColor(VaultColors.seal),
        singleLine = singleLine,
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle.copy(color = placeholderColor))
                }
                inner()
            }
        },
    )
}

@Composable
private fun MediaStubIcon(icon: ImageVector, label: String) {
    IconButton(
        onClick = { /* stub — media pickers deferred */ },
        modifier = Modifier.size(40.dp, 36.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = VaultColors.inkMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
