package com.nobi.nobistream2.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.util.Rational
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.nobi.nobistream2.core.FormatUtils
import com.nobi.nobistream2.ui.components.ErrorState
import com.nobi.nobistream2.ui.components.VideoThumb
import com.nobi.nobistream2.ui.player.PlayerSession
import com.nobi.nobistream2.ui.theme.NsColors
import kotlinx.coroutines.delay

/**
 * Player (Appendix A13/A14/A15): portrait 16:9 + info below, landscape
 * immersive fullscreen, Compose controls (3s auto-hide while playing),
 * double-tap ±10s, edge swipes for brightness/volume, seek bar, speed,
 * subtitles (CC only when a sidecar exists), PiP, lock, sleep timer,
 * queue sheet, Up-next overlay on autoplay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    session: PlayerSession,
    onBack: () -> Unit,
    onOpenVideo: (com.nobi.nobistream2.core.VideoItem, List<com.nobi.nobistream2.core.VideoItem>, Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val current by session.current.collectAsState()
    val isPlaying by session.isPlaying.collectAsState()
    val isBuffering by session.isBuffering.collectAsState()
    val positionMs by session.positionMs.collectAsState()
    val durationMs by session.durationMs.collectAsState()
    val error by session.error.collectAsState()
    val autoplay by session.autoplay.collectAsState()
    val speed by session.speed.collectAsState()
    val tracks by session.subtitleTracks.collectAsState()
    val subsEnabled by session.subtitlesEnabled.collectAsState()
    val sleepLeft by session.sleepMinutesLeft.collectAsState()
    val index by session.playlistIndex.collectAsState()
    val size by session.playlistSize.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }

    // Auto-hide controls 3s after they appear, only while playing.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }
    // Gesture label fades.
    LaunchedEffect(gestureLabel) {
        if (gestureLabel != null) {
            delay(1200)
            gestureLabel = null
        }
    }
    BackHandler {
        if (fullscreen) {
            fullscreen = false
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            session.stopAndSave()
            onBack()
        }
    }
    DisposableEffect(Unit) {
        onDispose { /* player lives in the session for the mini-player */ }
    }

    val item = current
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                .background(Color.Black)
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { if (!locked) controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (locked) return@detectTapGestures
                            val w = size.width.toFloat()
                            if (offset.x < w / 3) {
                                session.seekBy(-10_000)
                                gestureLabel = "-10s"
                            } else if (offset.x > 2 * w / 3) {
                                session.seekBy(10_000)
                                gestureLabel = "+10s"
                            } else {
                                session.togglePlayPause()
                            }
                        }
                    )
                }
                .pointerInput(locked) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (locked) return@detectVerticalDragGestures
                        val w = size.width.toFloat()
                        if (change.position.x < w / 3) {
                            // Left edge: brightness.
                            val window = activity?.window ?: return@detectVerticalDragGestures
                            val lp = window.attributes
                            val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                            lp.screenBrightness = (cur - dragAmount / 1000f).coerceIn(0.05f, 1f)
                            window.attributes = lp
                            gestureLabel = "Brightness ${(lp.screenBrightness * 100).toInt()}%"
                        } else if (change.position.x > 2 * w / 3) {
                            // Right edge: volume.
                            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val next = (cur - (dragAmount / 100).toInt()).coerceIn(0, max)
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                            gestureLabel = "Volume ${(next * 100 / max.coerceAtLeast(1))}%"
                        }
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = session.player
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                update = { view -> view.player = session.player },
                modifier = Modifier.fillMaxSize()
            )
            if (isBuffering) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NsColors.Primary)
                }
            }
            if (gestureLabel != null) {
                Box(
                    Modifier.align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(gestureLabel!!, color = Color.White)
                }
            }
            if (controlsVisible && !locked) {
                // Top gradient bar: back + title.
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        item?.title ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (size > 1) {
                        Text("${index + 1} / $size", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
                // Centre controls.
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    IconButton(onClick = { session.previous() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { session.togglePlayPause() }, modifier = Modifier.size(64.dp)) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play / Pause",
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    IconButton(onClick = { session.next() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }
                // Bottom bar: seek + options.
                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(FormatUtils.formatDuration((positionMs / 1000).toInt()), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = if (seeking) seekValue else if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                            onValueChange = { seeking = true; seekValue = it },
                            onValueChangeFinished = {
                                session.seekTo((seekValue * durationMs).toLong())
                                seeking = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text(FormatUtils.formatDuration((durationMs / 1000).toInt()), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { session.setAutoplay(!autoplay) }, modifier = Modifier.size(48.dp)) {
                            Text("Auto", color = if (autoplay) NsColors.Primary else Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = {
                            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                            val next = speeds[(speeds.indexOf(speed) + 1) % speeds.size]
                            session.setSpeed(next)
                        }, modifier = Modifier.size(48.dp)) {
                            Text("${speed}x", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                        if (tracks.isNotEmpty()) {
                            IconButton(onClick = { session.setSubtitlesEnabled(!subsEnabled, tracks.firstOrNull()?.language) }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    if (subsEnabled) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                                    contentDescription = "Subtitles",
                                    tint = if (subsEnabled) NsColors.Primary else Color.White
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showQueue = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Queue", tint = Color.White)
                        }
                        IconButton(onClick = {
                            val act = activity ?: return@IconButton
                            val params = PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()
                            act.enterPictureInPictureMode(params)
                        }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Picture-in-picture", tint = Color.White)
                        }
                        IconButton(onClick = {
                            fullscreen = !fullscreen
                            activity?.requestedOrientation = if (fullscreen) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { locked = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Lock, contentDescription = "Lock controls", tint = Color.White)
                        }
                    }
                }
            }
            if (locked) {
                IconButton(
                    onClick = { locked = false },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp).size(48.dp)
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = "Unlock controls", tint = Color.White)
                }
            }
            if (error != null) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    ErrorState(title = "Video couldn't play", body = error!!, onRetry = { session.retry() })
                }
            }
        }
        if (!fullscreen && item != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = NsColors.TextPrimary, maxLines = 2)
                Text(
                    "${item.quality.label} · ${FormatUtils.formatDuration(item.durationSec)} · ${FormatUtils.formatDate(item.dateEpochSec)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NsColors.TextSecondary
                )
                if (sleepLeft > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = NsColors.Warning, modifier = Modifier.size(16.dp))
                        Text("Sleep timer: $sleepLeft min left", style = MaterialTheme.typography.bodySmall, color = NsColors.Warning)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = { showQueue = true }) { Text("Queue") }
                    Spacer(Modifier.width(8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Sleep timer")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "60 min").forEach { (min, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    session.setSleepTimer(min)
                                    expanded = false
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(session = session, onDismiss = { showQueue = false })
    }
}

/** Appendix A14: queue — current highlighted, Up Next, tap to play. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(session: PlayerSession, onDismiss: () -> Unit) {
    val current by session.current.collectAsState()
    val index by session.playlistIndex.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Queue", style = MaterialTheme.typography.titleLarge, color = NsColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                items(session.playlist.withIndex().toList()) { (i, video) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (i == index) "▶ ${video.title}" else video.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (i == index) NsColors.Primary else NsColors.TextPrimary,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { session.playAt(i); onDismiss() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = NsColors.TextPrimary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Appendix A15: mini-player — 72dp, above the bottom nav, slide-up. */
@Composable
fun MiniPlayer(
    session: PlayerSession,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val current by session.current.collectAsState()
    val isPlaying by session.isPlaying.collectAsState()
    val item = current ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(NsColors.SurfaceHigh)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(NsColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = NsColors.TextDisabled)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = NsColors.TextPrimary, maxLines = 1)
            Text(item.quality.label, style = MaterialTheme.typography.bodySmall, color = NsColors.TextSecondary)
        }
        IconButton(onClick = { session.togglePlayPause() }, modifier = Modifier.size(48.dp)) {
            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play / Pause", tint = NsColors.TextPrimary)
        }
        IconButton(onClick = onOpenPlayer, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Fullscreen, contentDescription = "Open player", tint = NsColors.TextPrimary)
        }
    }
}
