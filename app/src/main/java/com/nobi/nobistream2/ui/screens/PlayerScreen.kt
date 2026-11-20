package com.nobi.nobistream2.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.nobi.nobistream2.core.FormatUtils
import com.nobi.nobistream2.data.NobiRepository
import com.nobi.nobistream2.ui.components.ErrorState
import com.nobi.nobistream2.ui.components.VideoThumb
import com.nobi.nobistream2.ui.player.PlayerSession
import com.nobi.nobistream2.ui.theme.ManropeFamily
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsIcons
import com.nobi.nobistream2.ui.theme.SoraFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Player (HTML redesign v1.1.0 — NobiStream 2 player): black bg, bottom
 * nav hidden, 16/9 video, X back + title top bar (NO Cast button — no
 * Cast backend exists), centre skip −10s / big 68dp play / skip +10s,
 * custom seek (track 4.5dp, buffered, accent fill, 14dp dot), time row,
 * control row Previous / CC / quality badge / lock / fullscreen, meta
 * below (title, sub, hold-tip chip, Up Next card). Engine unchanged
 * (Media3 + FFmpeg, resume, buffering, subtitles, speed, PiP session).
 * Hold-to-2x is REAL: long-press the video, release to restore speed.
 * Lock hides centre + bottom controls + title; unlock via the lock
 * button (top-right while locked).
 */
@Composable
fun PlayerScreen(
    session: PlayerSession,
    repo: NobiRepository,
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
    val index by session.playlistIndex.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    var holding2x by remember { mutableStateOf(false) }
    var prevSpeed by remember { mutableStateOf(1f) }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }
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
    val nextItem = session.playlist.getOrNull(index + 1)
    val durationSec = (durationMs / 1000).toInt()
    val positionSec = (positionMs / 1000).toInt()
    val remainingSec = (durationSec - positionSec).coerceAtLeast(0)
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val buffered = ((session.player?.bufferedPercentage ?: 0) / 100f).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                .background(Color.Black)
                .pointerInput(locked) {
                    // Single manual gesture block: tap toggles controls,
                    // double-tap seeks ±10s / toggles play, long-press holds
                    // 2x speed until release. (detectTapGestures' onPress
                    // cancels as soon as onLongPress fires, so it cannot
                    // implement hold-to-2x.)
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (locked) {
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        val up = withTimeoutOrNull(longPressTimeout) { waitForUpOrCancellation() }
                        if (up == null) {
                            // Long-press: 2x speed until the finger lifts.
                            prevSpeed = session.speed.value
                            session.setSpeed(2f)
                            holding2x = true
                            gestureLabel = "2× speed"
                            waitForUpOrCancellation()
                            session.setSpeed(prevSpeed)
                            holding2x = false
                            gestureLabel = null
                        } else {
                            val secondDown = withTimeoutOrNull(doubleTapTimeout) { awaitFirstDown() }
                            if (secondDown == null) {
                                controlsVisible = !controlsVisible
                            } else {
                                waitForUpOrCancellation()
                                val w = size.width.toFloat()
                                if (secondDown.position.x < w / 3) {
                                    session.seekBy(-10_000)
                                    gestureLabel = "-10s"
                                } else if (secondDown.position.x > 2 * w / 3) {
                                    session.seekBy(10_000)
                                    gestureLabel = "+10s"
                                } else {
                                    session.togglePlayPause()
                                }
                            }
                        }
                    }
                }
                .pointerInput(locked) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (locked) return@detectVerticalDragGestures
                        val w = size.width.toFloat()
                        if (change.position.x < w / 3) {
                            val window = activity?.window ?: return@detectVerticalDragGestures
                            val lp = window.attributes
                            val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                            lp.screenBrightness = (cur - dragAmount / 1000f).coerceIn(0.05f, 1f)
                            window.attributes = lp
                            gestureLabel = "Brightness ${(lp.screenBrightness * 100).toInt()}%"
                        } else if (change.position.x > 2 * w / 3) {
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
                    CircularProgressIndicator(color = NsColors.AccentBright)
                }
            }
            if (gestureLabel != null) {
                Box(
                    Modifier.align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(gestureLabel!!, color = Color.White)
                }
            }
            // Top bar: back (X) + title. Locked: title hidden, lock button stays tappable.
            if (controlsVisible || locked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                            )
                        )
                        .padding(10.dp, 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { session.stopAndSave(); onBack() }, modifier = Modifier.size(42.dp)) {
                        Icon(NsIcons.Close, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(21.dp))
                    }
                    if (!locked) {
                        Text(
                            item?.title ?: "",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.5.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { locked = false }, modifier = Modifier.size(42.dp)) {
                            Icon(NsIcons.Lock, contentDescription = "Unlock controls", tint = Color.White, modifier = Modifier.size(21.dp))
                        }
                    }
                }
            }
            // Centre controls.
            if (controlsVisible && !locked) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(34.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .size(52.dp)
                            .clickable {
                                session.seekBy(-10_000)
                                gestureLabel = "-10s"
                            }
                    ) {
                        Icon(NsIcons.SkipBack10, contentDescription = "Back 10 seconds", tint = Color.White, modifier = Modifier.size(27.dp))
                        Text("10", fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, color = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(NsColors.Primary)
                            .clickable { session.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) NsIcons.Pause else NsIcons.Play,
                            contentDescription = "Play / Pause",
                            tint = Color.White,
                            modifier = Modifier.size(27.dp).then(
                                if (!isPlaying) Modifier.padding(start = 3.dp) else Modifier
                            )
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .size(52.dp)
                            .clickable {
                                session.seekBy(10_000)
                                gestureLabel = "+10s"
                            }
                    ) {
                        Icon(NsIcons.SkipForward10, contentDescription = "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(27.dp))
                        Text("10", fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, color = Color.White)
                    }
                }
                // Bottom: seek + time + control row.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 8.dp)
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(22.dp)) {
                        val trackWidth = maxWidth
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.5.dp)
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.28f))
                                .pointerInput(durationMs) {
                                    detectTapGestures { offset ->
                                        if (durationMs > 0) {
                                            val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                            session.seekTo((frac * durationMs).toLong())
                                        }
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(buffered)
                                    .height(4.5.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.45f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(4.5.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(NsColors.AccentBright)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .offset(x = ((trackWidth * progress) - 7.dp).coerceAtLeast(0.dp))
                                .align(Alignment.CenterStart)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(NsColors.AccentBright)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            fmtClock(positionSec),
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NsColors.TimeRow
                        )
                        Text(
                            "-${fmtClock(remainingSec)} · ${fmtClock(durationSec)}",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NsColors.TimeRow
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { session.previous() }, modifier = Modifier.size(44.dp)) {
                            Icon(NsIcons.Previous, contentDescription = "Previous episode", tint = Color.White, modifier = Modifier.size(21.dp))
                        }
                        IconButton(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    session.setSubtitlesEnabled(!subsEnabled, tracks.firstOrNull()?.language)
                                } else {
                                    gestureLabel = "No subtitles for this video"
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                NsIcons.Captions,
                                contentDescription = "Subtitles",
                                tint = if (subsEnabled) NsColors.AccentBright else Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        // Quality badge (display only — each Telegram quality is a separate whole file, no ABR).
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .border(1.6.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                item?.quality?.label ?: "",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp,
                                color = Color.White
                            )
                        }
                        IconButton(onClick = { locked = true }, modifier = Modifier.size(44.dp)) {
                            Icon(NsIcons.Lock, contentDescription = "Lock controls", tint = Color.White, modifier = Modifier.size(21.dp))
                        }
                        IconButton(
                            onClick = {
                                fullscreen = !fullscreen
                                activity?.requestedOrientation = if (fullscreen) {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(NsIcons.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(21.dp))
                        }
                    }
                }
            }
            if (error != null) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    ErrorState(title = "Video couldn't play", body = error!!, onRetry = { session.retry() })
                }
            }
        }
        // Meta below the video (portrait only).
        if (!fullscreen && item != null) {
            Column(modifier = Modifier.fillMaxWidth().background(NsColors.Background).padding(18.dp, 20.dp, 20.dp, 26.dp)) {
                Text(
                    item.title,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = NsColors.TextPrimary,
                    maxLines = 2
                )
                Text(
                    "${item.quality.label} · ${FormatUtils.formatDuration(item.durationSec)} · ${FormatUtils.formatDate(item.dateEpochSec)}",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = NsColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NsColors.Surface)
                        .border(1.dp, NsColors.Divider, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("Tip — screen hold karke rakho, video ")
                            withStyle(androidx.compose.ui.text.SpanStyle(color = NsColors.AccentBright, fontWeight = FontWeight.Bold)) {
                                append("2× speed")
                            }
                            append(" pe chalega")
                        },
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = NsColors.TextSecondary
                    )
                }
                Spacer(Modifier.height(18.dp))
                if (nextItem != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(NsDimens.CornerCard))
                            .background(NsColors.Surface)
                            .border(1.dp, NsColors.Divider, RoundedCornerShape(NsDimens.CornerCard))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "UP NEXT",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = NsColors.TextDisabled,
                                letterSpacing = 0.1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(NsColors.Primary.copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    if (autoplay) "Autoplay on" else "Autoplay off",
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = NsColors.AccentBright
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { session.next() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VideoThumb(
                                repo = repo,
                                item = nextItem,
                                showBadges = false,
                                modifier = Modifier.width(96.dp).aspectRatio(16f / 9f)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    nextItem.title,
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.5.sp,
                                    color = NsColors.TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    listOfNotNull(
                                        nextItem.quality.label.takeIf { nextItem.quality.label != "Unknown" },
                                        FormatUtils.formatDuration(nextItem.durationSec).takeIf { nextItem.durationSec > 0 },
                                        FormatUtils.formatSize(nextItem.sizeBytes).takeIf { nextItem.sizeBytes > 0 }
                                    ).joinToString(" · "),
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = NsColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fmtClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
    else "%02d:%02d".format(m, sec)
}

/** Mini-player — 72dp, above the bottom nav, slide-up. */
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
            Icon(NsIcons.Play, contentDescription = null, tint = NsColors.TextDisabled)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = NsColors.TextPrimary, maxLines = 1)
            Text(item.quality.label, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = NsColors.TextSecondary)
        }
        IconButton(onClick = { session.togglePlayPause() }, modifier = Modifier.size(48.dp)) {
            Icon(
                if (isPlaying) NsIcons.Pause else NsIcons.Play,
                contentDescription = "Play / Pause",
                tint = NsColors.TextPrimary
            )
        }
        IconButton(onClick = onOpenPlayer, modifier = Modifier.size(48.dp)) {
            Icon(NsIcons.Fullscreen, contentDescription = "Open player", tint = NsColors.TextPrimary)
        }
    }
}
