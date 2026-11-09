package com.silasha.diaryvault.ui.diary

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.silasha.diaryvault.R
import com.silasha.diaryvault.config.DiaryMediaConfig
import com.silasha.diaryvault.media.DiaryMediaFileStore
import com.silasha.diaryvault.media.MediaFileError
import com.silasha.diaryvault.model.DiaryMediaAttachment
import com.silasha.diaryvault.model.DiaryMediaKind
import com.silasha.diaryvault.sync.DiaryMediaCloudSync
import com.silasha.diaryvault.ui.components.localizedString
import com.silasha.diaryvault.ui.theme.VaultColors
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ComposerMediaDock(
    entryId: String,
    attachments: List<DiaryMediaAttachment>,
    onAttachmentsChange: (List<DiaryMediaAttachment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var voiceElapsed by remember { mutableFloatStateOf(0f) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceTempFile by remember { mutableStateOf<File?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var showPhotoSource by remember { mutableStateOf(false) }
    var showVideoSource by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoFile by remember { mutableStateOf<File?>(null) }

    fun toast(resId: Int, vararg args: Any) {
        val msg = if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun refuseIfAtCap(kind: DiaryMediaKind): Boolean {
        if (!DiaryMediaConfig.canAdd(kind, attachments)) {
            toast(
                R.string.diary_media_limit_kind,
                context.getString(
                    when (kind) {
                        DiaryMediaKind.Voice -> R.string.diary_media_voice
                        DiaryMediaKind.Image -> R.string.diary_media_photo
                        DiaryMediaKind.Video -> R.string.diary_media_video
                    },
                ),
                DiaryMediaConfig.maxCount(kind),
            )
            return true
        }
        if (attachments.size >= DiaryMediaConfig.maxAttachmentsPerEntry) {
            toast(R.string.diary_media_limit_total, DiaryMediaConfig.maxAttachmentsPerEntry)
            return true
        }
        return false
    }

    fun addAttachment(item: DiaryMediaAttachment) {
        onAttachmentsChange(attachments + item)
    }

    fun removeAttachment(item: DiaryMediaAttachment) {
        DiaryMediaFileStore.delete(context, entryId, item)
        onAttachmentsChange(attachments.filterNot { it.id == item.id })
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            try {
                val working = attachments.toMutableList()
                var added = 0
                var skipped = 0
                for (uri in uris) {
                    if (!DiaryMediaConfig.canAdd(DiaryMediaKind.Image, working)) {
                        skipped++
                        continue
                    }
                    try {
                        val item = withContext(Dispatchers.IO) {
                            DiaryMediaFileStore.saveImageJpeg(context, entryId, uri)
                        }
                        working.add(item)
                        added++
                    } catch (_: Exception) {
                        skipped++
                    }
                }
                if (added > 0) onAttachmentsChange(working)
                if (skipped > 0 && added > 0) {
                    toast(R.string.diary_media_limit_partial, added, skipped)
                } else if (added == 0 && uris.isNotEmpty()) {
                    toast(R.string.diary_media_save_failed)
                }
            } finally {
                isImporting = false
            }
        }
    }

    val pickVideos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            try {
                val working = attachments.toMutableList()
                for (uri in uris) {
                    if (!DiaryMediaConfig.canAdd(DiaryMediaKind.Video, working)) {
                        toast(
                            R.string.diary_media_limit_kind,
                            context.getString(R.string.diary_media_video),
                            DiaryMediaConfig.maxCount(DiaryMediaKind.Video),
                        )
                        break
                    }
                    try {
                        val item = withContext(Dispatchers.IO) {
                            DiaryMediaFileStore.importVideo(context, entryId, uri)
                        }
                        working.add(item)
                    } catch (_: MediaFileError.VideoTooLong) {
                        toast(R.string.diary_media_video_too_long)
                    } catch (_: Exception) {
                        toast(R.string.diary_media_save_failed)
                    }
                }
                if (working.size > attachments.size) onAttachmentsChange(working)
            } finally {
                isImporting = false
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (!ok || uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            try {
                if (refuseIfAtCap(DiaryMediaKind.Image)) return@launch
                val item = withContext(Dispatchers.IO) {
                    DiaryMediaFileStore.saveImageJpeg(context, entryId, uri)
                }
                addAttachment(item)
            } catch (_: Exception) {
                toast(R.string.diary_media_save_failed)
            } finally {
                isImporting = false
            }
        }
    }

    val captureVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { ok ->
        val file = pendingVideoFile
        pendingVideoFile = null
        if (!ok || file == null || !file.isFile) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            try {
                if (refuseIfAtCap(DiaryMediaKind.Video)) {
                    file.delete()
                    return@launch
                }
                val item = withContext(Dispatchers.IO) {
                    DiaryMediaFileStore.importVideoFile(context, entryId, file)
                }
                addAttachment(item)
            } catch (e: MediaFileError.VideoTooLong) {
                toast(R.string.diary_media_video_too_long)
            } catch (_: Exception) {
                toast(R.string.diary_media_save_failed)
            } finally {
                file.delete()
                isImporting = false
            }
        }
    }

    val requestMic = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startVoiceRecordingInternal(
            context = context,
            entryId = entryId,
            onRecorder = { recorder = it },
            onTempFile = { voiceTempFile = it },
            onRecording = { isRecording = it },
            onFail = { toast(R.string.diary_media_record_failed) },
        ) else {
            toast(R.string.diary_media_permission_mic)
        }
    }

    val requestCameraForPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            toast(R.string.diary_media_permission_camera)
            return@rememberLauncherForActivityResult
        }
        val captureDir = File(context.cacheDir, "media_capture").also { it.mkdirs() }
        val file = File(captureDir, "cam_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri
        takePicture.launch(uri)
    }

    val requestCameraForVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            toast(R.string.diary_media_permission_camera)
            return@rememberLauncherForActivityResult
        }
        val captureDir = File(context.cacheDir, "media_capture").also { it.mkdirs() }
        val file = File(captureDir, "vid_${UUID.randomUUID()}.mp4")
        pendingVideoFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        captureVideo.launch(uri)
    }

    // Hard-stop voice at 90s.
    LaunchedEffect(isRecording) {
        if (!isRecording) {
            voiceElapsed = 0f
            return@LaunchedEffect
        }
        val start = System.currentTimeMillis()
        while (isActive && isRecording) {
            voiceElapsed = (System.currentTimeMillis() - start) / 1000f
            if (voiceElapsed >= DiaryMediaConfig.voiceMaxDurationSeconds) {
                stopVoiceRecording(
                    context = context,
                    entryId = entryId,
                    recorder = recorder,
                    tempFile = voiceTempFile,
                    elapsed = voiceElapsed.toDouble(),
                    onClear = {
                        recorder = null
                        voiceTempFile = null
                        isRecording = false
                    },
                    onSaved = { addAttachment(it) },
                    onFail = { toast(R.string.diary_media_save_failed) },
                )
                break
            }
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.release() }
            voiceTempFile?.delete()
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (attachments.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { item ->
                    AttachmentChip(item = item, onRemove = { removeAttachment(item) })
                }
            }
        }

        if (isRecording) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(VaultColors.seal),
                )
                Text(
                    localizedString(R.string.diary_media_recording),
                    color = VaultColors.ink,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "%d″".format(voiceElapsed.toInt()),
                    color = VaultColors.inkMuted,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        stopVoiceRecording(
                            context = context,
                            entryId = entryId,
                            recorder = recorder,
                            tempFile = voiceTempFile,
                            elapsed = voiceElapsed.toDouble(),
                            onClear = {
                                recorder = null
                                voiceTempFile = null
                                isRecording = false
                            },
                            onSaved = { addAttachment(it) },
                            onFail = { toast(R.string.diary_media_save_failed) },
                        )
                    },
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = VaultColors.seal)
                    Spacer(Modifier.width(4.dp))
                    Text(localizedString(R.string.diary_media_stop_save), color = VaultColors.seal)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaToolIcon(
                    icon = Icons.Default.Mic,
                    label = localizedString(R.string.diary_media_voice),
                    enabled = !isImporting,
                ) {
                    if (refuseIfAtCap(DiaryMediaKind.Voice)) return@MediaToolIcon
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        startVoiceRecordingInternal(
                            context = context,
                            entryId = entryId,
                            onRecorder = { recorder = it },
                            onTempFile = { voiceTempFile = it },
                            onRecording = { isRecording = it },
                            onFail = { toast(R.string.diary_media_record_failed) },
                        )
                    } else {
                        requestMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                MediaToolIcon(
                    icon = Icons.Default.Image,
                    label = localizedString(R.string.diary_media_photo),
                    enabled = !isImporting,
                ) {
                    if (refuseIfAtCap(DiaryMediaKind.Image)) return@MediaToolIcon
                    showPhotoSource = true
                }
                MediaToolIcon(
                    icon = Icons.Default.Videocam,
                    label = localizedString(R.string.diary_media_video),
                    enabled = !isImporting,
                ) {
                    if (refuseIfAtCap(DiaryMediaKind.Video)) return@MediaToolIcon
                    showVideoSource = true
                }
                Spacer(Modifier.weight(1f))
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = VaultColors.seal,
                    )
                } else {
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
        }
    }

    if (showPhotoSource) {
        AlertDialog(
            onDismissRequest = { showPhotoSource = false },
            title = { Text(localizedString(R.string.diary_media_photo_source)) },
            text = {
                Column {
                    TextButton(onClick = {
                        showPhotoSource = false
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val captureDir = File(context.cacheDir, "media_capture").also { it.mkdirs() }
                            val file = File(captureDir, "cam_${UUID.randomUUID()}.jpg")
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            pendingCameraUri = uri
                            takePicture.launch(uri)
                        } else {
                            requestCameraForPhoto.launch(Manifest.permission.CAMERA)
                        }
                    }) { Text(localizedString(R.string.diary_media_take_photo)) }
                    TextButton(onClick = {
                        showPhotoSource = false
                        pickImages.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }) { Text(localizedString(R.string.diary_media_choose_photo)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoSource = false }) {
                    Text(localizedString(R.string.common_cancel))
                }
            },
        )
    }

    if (showVideoSource) {
        AlertDialog(
            onDismissRequest = { showVideoSource = false },
            title = { Text(localizedString(R.string.diary_media_video_source)) },
            text = {
                Column {
                    TextButton(onClick = {
                        showVideoSource = false
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val captureDir = File(context.cacheDir, "media_capture").also { it.mkdirs() }
                            val file = File(captureDir, "vid_${UUID.randomUUID()}.mp4")
                            pendingVideoFile = file
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            captureVideo.launch(uri)
                        } else {
                            requestCameraForVideo.launch(Manifest.permission.CAMERA)
                        }
                    }) { Text(localizedString(R.string.diary_media_record_video)) }
                    TextButton(onClick = {
                        showVideoSource = false
                        pickVideos.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    }) { Text(localizedString(R.string.diary_media_choose_video)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVideoSource = false }) {
                    Text(localizedString(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun MediaToolIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp, 36.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) VaultColors.inkMuted else VaultColors.inkMuted.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AttachmentChip(item: DiaryMediaAttachment, onRemove: () -> Unit) {
    val label = when (item.kind) {
        DiaryMediaKind.Voice -> localizedString(R.string.diary_media_voice)
        DiaryMediaKind.Image -> localizedString(R.string.diary_media_photo)
        DiaryMediaKind.Video -> localizedString(R.string.diary_media_video)
    }
    val duration = item.durationSeconds?.toInt()?.let { " · ${it}″" }.orEmpty()
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(VaultColors.line.copy(alpha = 0.45f))
            .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$label$duration",
            style = MaterialTheme.typography.labelLarge,
            color = VaultColors.ink,
            fontSize = 12.sp,
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = localizedString(R.string.diary_media_remove),
                tint = VaultColors.inkMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun startVoiceRecordingInternal(
    context: android.content.Context,
    entryId: String,
    onRecorder: (MediaRecorder) -> Unit,
    onTempFile: (File) -> Unit,
    onRecording: (Boolean) -> Unit,
    onFail: () -> Unit,
) {
    try {
        DiaryMediaFileStore.ensureEntryDirectory(context, entryId)
        val temp = File(context.cacheDir, "voice_${UUID.randomUUID()}.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setAudioEncodingBitRate(128_000)
        mr.setAudioSamplingRate(44_100)
        mr.setOutputFile(temp.absolutePath)
        mr.prepare()
        mr.start()
        onTempFile(temp)
        onRecorder(mr)
        onRecording(true)
    } catch (_: Exception) {
        onFail()
    }
}

private fun stopVoiceRecording(
    context: android.content.Context,
    entryId: String,
    recorder: MediaRecorder?,
    tempFile: File?,
    elapsed: Double,
    onClear: () -> Unit,
    onSaved: (DiaryMediaAttachment) -> Unit,
    onFail: () -> Unit,
) {
    try {
        recorder?.apply {
            runCatching { stop() }
            release()
        }
        onClear()
        val src = tempFile
        if (src == null || !src.isFile || src.length() < 64) {
            src?.delete()
            onFail()
            return
        }
        val item = DiaryMediaFileStore.importVoiceRecording(
            context = context,
            entryId = entryId,
            source = src,
            durationSeconds = elapsed.coerceAtMost(DiaryMediaConfig.voiceMaxDurationSeconds),
        )
        src.delete()
        onSaved(item)
    } catch (_: Exception) {
        onFail()
    }
}

// region Detail playback

@Composable
fun DiaryMediaSections(entryId: String, attachments: List<DiaryMediaAttachment>) {
    if (attachments.isEmpty()) return
    val voices = attachments.filter { it.kind == DiaryMediaKind.Voice }
    val images = attachments.filter { it.kind == DiaryMediaKind.Image }
    val videos = attachments.filter { it.kind == DiaryMediaKind.Video }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (voices.isNotEmpty()) {
            Text(localizedString(R.string.diary_media_voice), style = MaterialTheme.typography.titleMedium)
            voices.forEach { DiaryAudioAttachmentView(entryId, it) }
        }
        if (images.isNotEmpty()) {
            Text(localizedString(R.string.diary_media_photo), style = MaterialTheme.typography.titleMedium)
            images.forEach { DiaryImageAttachmentView(entryId, it) }
        }
        if (videos.isNotEmpty()) {
            Text(localizedString(R.string.diary_media_video), style = MaterialTheme.typography.titleMedium)
            videos.forEach { DiaryVideoAttachmentView(entryId, it) }
        }
    }
}

@Composable
private fun DiaryImageAttachmentView(entryId: String, attachment: DiaryMediaAttachment) {
    val context = LocalContext.current
    var file by remember(attachment.id) { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(attachment.id, attachment.remotePath) {
        try {
            file = withContext(Dispatchers.IO) {
                DiaryMediaCloudSync.ensureLocalFile(context, entryId, attachment)
            }
        } catch (_: Exception) {
            error = true
        }
    }

    when {
        error -> Text(
            localizedString(R.string.diary_media_unavailable),
            color = VaultColors.inkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        file == null -> CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = VaultColors.seal,
            strokeWidth = 2.dp,
        )
        else -> {
            // Prefer Coil when present; otherwise BitmapFactory via AsyncImage won't work without Coil.
            // Use AndroidView ImageView to avoid a new Coil dependency.
            AndroidView(
                factory = { ctx ->
                    android.widget.ImageView(ctx).apply {
                        adjustViewBounds = true
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    }
                },
                update = { view ->
                    val bmp = android.graphics.BitmapFactory.decodeFile(file!!.absolutePath)
                    view.setImageBitmap(bmp)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun DiaryAudioAttachmentView(entryId: String, attachment: DiaryMediaAttachment) {
    val context = LocalContext.current
    var file by remember(attachment.id) { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(attachment.id, attachment.remotePath) {
        try {
            file = withContext(Dispatchers.IO) {
                DiaryMediaCloudSync.ensureLocalFile(context, entryId, attachment)
            }
        } catch (_: Exception) {
            error = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            error -> Text(
                localizedString(R.string.diary_media_unavailable),
                color = VaultColors.inkMuted,
            )
            file == null -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = VaultColors.seal,
            )
            else -> {
                IconButton(
                    onClick = {
                        if (playing) {
                            player?.pause()
                            playing = false
                        } else {
                            try {
                                if (player == null) {
                                    player = MediaPlayer().apply {
                                        setDataSource(file!!.absolutePath)
                                        prepare()
                                        setOnCompletionListener { playing = false }
                                    }
                                }
                                player?.start()
                                playing = true
                            } catch (_: Exception) {
                                error = true
                            }
                        }
                    },
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = localizedString(R.string.diary_media_voice),
                        tint = VaultColors.seal,
                    )
                }
                val dur = attachment.durationSeconds?.toInt()
                Text(
                    if (dur != null) localizedString(R.string.diary_media_voice) + " · ${dur}″"
                    else localizedString(R.string.diary_media_voice),
                    style = MaterialTheme.typography.bodyLarge,
                    color = VaultColors.ink,
                )
            }
        }
    }
}

@Composable
private fun DiaryVideoAttachmentView(entryId: String, attachment: DiaryMediaAttachment) {
    val context = LocalContext.current
    var file by remember(attachment.id) { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(attachment.id, attachment.remotePath) {
        try {
            file = withContext(Dispatchers.IO) {
                DiaryMediaCloudSync.ensureLocalFile(context, entryId, attachment)
            }
        } catch (_: Exception) {
            error = true
        }
    }

    when {
        error -> Text(
            localizedString(R.string.diary_media_unavailable),
            color = VaultColors.inkMuted,
        )
        file == null -> CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = VaultColors.seal,
            strokeWidth = 2.dp,
        )
        else -> {
            val exo = remember(file!!.absolutePath) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    prepare()
                }
            }
            DisposableEffect(exo) {
                onDispose { exo.release() }
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exo
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
            )
        }
    }
}

// endregion
