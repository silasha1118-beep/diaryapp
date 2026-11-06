package com.diaryvault.app.ui.diary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.billing.BillingConfig
import com.diaryvault.app.media.DiaryMediaFileStore
import com.diaryvault.app.model.DiaryEntry
import com.diaryvault.app.model.DiaryMediaKind
import com.diaryvault.app.ui.components.VaultBrandHeader
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.paywall.UpgradeCheckoutSheet
import com.diaryvault.app.ui.theme.VaultColors
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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

    val compact = LocalConfiguration.current.screenHeightDp < 720
    // Equal horizontal inset on narrow phones (Redmi 9C ~360dp) — prior start=24/end=20
    // looked right-shifted; rows stay full-bleed so swipe Delete sits on the screen edge.
    val horizontalInset = if (LocalConfiguration.current.screenWidthDp < 400) 16.dp else 20.dp
    // Android convention: bottom-right circular Material + FAB (not iOS top square.and.pencil).
    // contentWindowInsets=0: MainTabs already clears bottom nav; avoid double system-bar padding that
    // shoved FAB / list into overlapping chrome on short Redmi screens.
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                containerColor = VaultColors.seal,
                contentColor = VaultColors.paper,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = localizedString(R.string.diary_new))
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(padding)
                    .padding(horizontal = horizontalInset, vertical = if (compact) 12.dp else 20.dp),
            ) {
                VaultBrandHeader(
                    title = localizedString(R.string.brand_name),
                    subtitle = subtitle,
                    compact = compact,
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
                modifier = Modifier.statusBarsPadding(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = padding.calculateTopPadding() + if (compact) 8.dp else 12.dp,
                    // FAB clearance above bottom tab bar (MainTabs padding); keep list readable.
                    bottom = padding.calculateBottomPadding() + if (compact) 72.dp else 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    VaultBrandHeader(
                        title = localizedString(R.string.brand_name),
                        subtitle = subtitle,
                        compact = compact,
                        modifier = Modifier
                            .padding(horizontal = horizontalInset)
                            .padding(bottom = if (compact) 14.dp else 20.dp),
                    )
                }
                items(entries, key = { it.id }) { item ->
                    // Reveal Delete on swipe; tap soft-deletes immediately (never full-swipe auto-delete).
                    DiarySwipeRow(
                        entry = item,
                        contentInset = horizontalInset,
                        onOpen = { onOpen(item.id) },
                        onDelete = { viewModel.deleteEntry(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DiarySwipeRow(
    entry: DiaryEntry,
    contentInset: Dp,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val swipeHint = localizedString(R.string.diary_swipe_hint)
    val deleteLabel = localizedString(R.string.common_delete)
    val actionWidth = 88.dp
    val density = LocalDensity.current
    val actionPx = with(density) { actionWidth.toPx() }
    val scope = rememberCoroutineScope()
    var offsetX by remember(entry.id) { mutableFloatStateOf(0f) }

    fun settleReveal(open: Boolean) {
        val target = if (open) -actionPx else 0f
        scope.launch {
            val anim = Animatable(offsetX)
            anim.animateTo(target, tween(200)) {
                offsetX = value
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        offsetX = (offsetX + delta).coerceIn(-actionPx, 0f)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = swipeHint },
    ) {
        // Seal Delete action — revealed behind content; tap soft-deletes (full swipe never deletes).
        Box(
            Modifier
                .matchParentSize()
                .background(VaultColors.seal),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                Modifier
                    .width(actionWidth)
                    .fillMaxHeight()
                    .clickable {
                        settleReveal(false)
                        onDelete()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = deleteLabel,
                    tint = VaultColors.paper,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    deleteLabel,
                    color = VaultColors.paper,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Column(
            Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .background(VaultColors.paper)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        settleReveal(offsetX <= -actionPx * 0.45f)
                    },
                ),
        ) {
            DiaryRow(
                entry = entry,
                contentInset = contentInset,
                onClick = {
                    if (offsetX < -actionPx * 0.2f) {
                        settleReveal(false)
                    } else {
                        onOpen()
                    }
                },
            )
            HorizontalDivider(
                color = VaultColors.line.copy(alpha = 0.55f),
                modifier = Modifier.padding(start = contentInset),
            )
        }
    }
}

@Composable
private fun DiaryRow(
    entry: DiaryEntry,
    contentInset: Dp,
    onClick: () -> Unit,
) {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(entry.createdAt))
    val previewText = when {
        entry.preview.isNotBlank() -> entry.preview
        entry.hasMedia -> localizedString(R.string.diary_media_preview_only)
        else -> localizedString(R.string.diary_empty_body_placeholder)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = contentInset, vertical = 14.dp),
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
            if (entry.hasMedia) {
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.mediaKindsPresent.forEach { kind ->
                        Icon(
                            imageVector = when (kind) {
                                DiaryMediaKind.Voice -> Icons.Default.Mic
                                DiaryMediaKind.Image -> Icons.Default.Image
                                DiaryMediaKind.Video -> Icons.Default.Videocam
                            },
                            contentDescription = null,
                            tint = VaultColors.inkMuted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = entry.title.ifBlank { localizedString(R.string.diary_empty_body_placeholder) },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = previewText,
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
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
                if (entry.body.isNotBlank()) {
                    Text(entry.body, style = MaterialTheme.typography.bodyLarge)
                } else if (!entry.hasMedia) {
                    Text(
                        localizedString(R.string.diary_empty_body_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (entry.hasMedia) {
                    Spacer(Modifier.height(20.dp))
                    DiaryMediaSections(entryId = entry.id, attachments = entry.attachments)
                }
                Spacer(Modifier.height(24.dp))
                Text(localizedString(R.string.diary_detail_owned), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(32.dp))
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
    val context = LocalContext.current
    val draftEntryId = remember {
        existing?.id ?: UUID.randomUUID().toString()
    }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var body by remember { mutableStateOf(existing?.body.orEmpty()) }
    var mood by remember { mutableStateOf(existing?.moodTag.orEmpty()) }
    var attachments by remember {
        mutableStateOf(existing?.attachments.orEmpty())
    }
    var hardTitle by remember { mutableStateOf<String?>(null) }
    var hardBenefit by remember { mutableStateOf<String?>(null) }
    var hardMeta by remember { mutableStateOf<String?>(null) }
    var hardShowUpgrade by remember { mutableStateOf(false) }
    var hardUpgradePro by remember { mutableStateOf(false) }
    var showUpgradeCheckout by remember { mutableStateOf(false) }
    // Mirror iOS: canSave when body has text OR attachments present.
    val canSave = body.isNotBlank() || attachments.isNotEmpty()

    fun discardDraftMediaIfNeeded() {
        if (existing == null) {
            DiaryMediaFileStore.deleteAll(context, draftEntryId)
        }
    }

    fun trySave() {
        if (!canSave) return
        if (existing != null) {
            viewModel.updateEntry(
                existing.copy(
                    title = title.trim(),
                    body = body.trim(),
                    moodTag = mood.trim().ifBlank { null },
                    attachments = attachments,
                ),
            )
            onDone()
            return
        }
        when (
            val result = viewModel.addEntry(
                title = title,
                body = body,
                moodTag = mood,
                attachments = attachments,
                entryId = draftEntryId,
            )
        ) {
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    TextButton(onClick = {
                        discardDraftMediaIfNeeded()
                        onCancel()
                    }) {
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(VaultColors.paper)
                    .imePadding(),
            ) {
                HorizontalDivider(color = VaultColors.line.copy(alpha = 0.7f))
                ComposerMediaDock(
                    entryId = draftEntryId,
                    attachments = attachments,
                    onAttachmentsChange = { attachments = it },
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
                )
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
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { showUpgradeCheckout = true },
                        )
                    }
                }
            }
        }
    }

    if (showUpgradeCheckout) {
        UpgradeCheckoutSheet(
            viewModel = viewModel,
            preferredTier = if (hardUpgradePro) {
                BillingConfig.Tier.AUTHOR_PRO
            } else {
                BillingConfig.Tier.AUTHOR_PLUS
            },
            onDismiss = { showUpgradeCheckout = false },
        )
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

