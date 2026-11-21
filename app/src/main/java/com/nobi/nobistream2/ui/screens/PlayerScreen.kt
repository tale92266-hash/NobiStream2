package com.nobi.nobistream2.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import com.nobi.nobistream2.core.FormatUtils
import com.nobi.nobistream2.core.SmartFilter
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.data.NobiRepository
import com.nobi.nobistream2.ui.components.CopiesSheet
import com.nobi.nobistream2.ui.components.ErrorState
import com.nobi.nobistream2.ui.components.VideoThumb
import com.nobi.nobistream2.ui.player.PlayerSession
import com.nobi.nobistream2.ui.theme.ManropeFamily
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens
import com.nobi.nobistream2.ui.theme.NsIcons
import com.nobi.nobistream2.ui.theme.SoraFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Player (HTML redesign v1.1.0 — NobiStream 2 player): black bg, bottom
 * nav hidden, 16/9 video, X back + title top bar (NO Cast button — no
 * Cast backend exists), centre controls — skip −10s / previous episode /
 * big 68dp play / next episode / skip +10s (v1.1.3: prev/next moved here
 * from the bottom row, Sam's order), custom seek (track 4.5dp, buffered,
 * accent fill, 14dp dot), time row, control row CC / quality badge /
 * lock / fullscreen, meta below (title, sub, hold-tip chip, Up Next
 * horizontal rail in portrait, YouTube-style). Engine unchanged
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
    // v1.1.1 item 2: 15-step volume/brightness overlay (0-15, 15 = 100%).
    var stepOverlay by remember { mutableStateOf<StepOverlay?>(null) }
    var stepOverlayTick by remember { mutableIntStateOf(0) }
    var holding2x by remember { mutableStateOf(false) }
    var prevSpeed by remember { mutableStateOf(1f) }
    // v1.1.2 item 7b: lock cluster (title + seekbar + unlock) shows
    // and hides TOGETHER on one 3s timer — NS1 v9.9 pattern. The tick
    // restarts the timer on every tap.
    var lockClusterVisible by remember { mutableStateOf(false) }
    var lockClusterTick by remember { mutableIntStateOf(0) }
    // v1.1.2 item 5: quality picker — copies of this episode.
    var qualityPickerOpen by remember { mutableStateOf(false) }
    var pickerCopies by remember { mutableStateOf<List<VideoItem>?>(null) }
    var pickerLoading by remember { mutableStateOf(false) }
    var pickerError by remember { mutableStateOf(false) }
    // v1.1.2 item 7a: seek preview — where the drag WILL land, shown
    // first; the seek commits only on release.
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    // A new episode invalidates any picker copies loaded for the old one.
    LaunchedEffect(current?.messageId) {
        pickerCopies = null
        pickerError = false
    }
    LaunchedEffect(lockClusterVisible, lockClusterTick) {
        if (lockClusterVisible) {
            delay(3000)
            lockClusterVisible = false
        }
    }
    // The gesture detector reads controlsVisible through a ref so it is
    // not restarted (and a drag cancelled) when controls auto-hide.
    val controlsVisibleRef = rememberUpdatedState(controlsVisible)

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
    LaunchedEffect(stepOverlayTick) {
        if (stepOverlay != null) {
            delay(1200)
            stepOverlay = null
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
    // v1.1.1 item 10: brightness is PLAYER-ONLY. Capture the window's
    // brightness the moment the player opens; restore it on dispose so
    // Home/Library/Channel never inherit the player's brightness. The
    // system setting is never written (no WRITE_SETTINGS route anywhere).
    val originalBrightness = remember { activity?.window?.attributes?.screenBrightness ?: -1f }
    DisposableEffect(Unit) {
        onDispose {
            val w = activity?.window
            if (w != null) {
                val lp = w.attributes
                lp.screenBrightness = originalBrightness
                w.attributes = lp
            }
            /* the player itself lives in the session for the mini-player */
        }
    }
    // v1.1.1 item 3: true immersive fullscreen — hide the status AND
    // navigation bars while fullscreen, restore on exit/dispose.
    // Portrait player and channel screens are untouched.
    DisposableEffect(fullscreen, activity) {
        val w = activity?.window
        if (w != null) {
            val controller = WindowCompat.getInsetsController(w, w.decorView)
            if (fullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val win = activity?.window
            if (win != null) {
                WindowCompat.getInsetsController(win, win.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val item = current
    val playlistSize by session.playlistSize.collectAsState()
    // v1.1.1 item 12: Up Next = the next 2-3 episodes, never fake rows.
    val upNext = remember(index, playlistSize) {
        (1..3).mapNotNull { session.playlist.getOrNull(index + it) }
    }
    // v1.1.3 item 2: the portrait Up Next RAIL scrolls horizontally
    // (YouTube-style) — the next 10 episodes, so there is something
    // to swipe through. Fewer remaining = fewer cards, never fake rows.
    val upNextRail = remember(index, playlistSize) {
        (1..10).mapNotNull { session.playlist.getOrNull(index + it) }
    }
    val nextItem = upNext.firstOrNull()
    val durationSec = (durationMs / 1000).toInt()
    val positionSec = (positionMs / 1000).toInt()
    val remainingSec = (durationSec - positionSec).coerceAtLeast(0)
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val buffered = ((session.player?.bufferedPercentage ?: 0) / 100f).coerceIn(0f, 1f)

    // v1.1.2 item 5: (re)load this episode's copies for the picker.
    val loadPickerCopies: () -> Unit = {
        val cur = item
        if (cur != null && !pickerLoading) {
            pickerLoading = true
            pickerError = false
            scope.launch {
                pickerCopies = if (session.downloadLocalPath != null) {
                    listOf(cur)
                } else {
                    loadCopiesFor(repo, session.chatId, cur)
                }
                pickerLoading = false
                if (pickerCopies == null) pickerError = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                .background(Color.Black)
                .pointerInput(locked, durationMs) {
                    // v1.1.1 items 2/4/5: ONE detector owns the whole
                    // video area. Tap toggles controls, double-tap seeks
                    // ±10s by thirds (or toggles play in the middle),
                    // long-press holds 2x until release. A drag is
                    // direction-locked on the first movement: horizontal
                    // = seek (full width = 60s, live time preview, commit
                    // on release), vertical = brightness (left half) /
                    // volume (right half) on a 0-15 scale (15 = 100%).
                    // Drags starting in the top/bottom 48dp bands (shade
                    // + gesture-nav zone) are ignored entirely. Because
                    // taps and drags live in ONE detector, a drag can
                    // never be misread as a long-press (the v1.1.0 bug:
                    // the vertical-drag detector cancelled the tap
                    // detector's wait, which fired a 2x flash).
                    val touchSlop = viewConfiguration.touchSlop
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                    val topExclusionPx = TOP_EDGE_GESTURE_EXCLUSION_DP.dp.toPx()
                    val bottomExclusionPx = BOTTOM_EDGE_GESTURE_EXCLUSION_DP.dp.toPx()
                    val topStripPx = 64.dp.toPx()
                    val bottomStripPx = 116.dp.toPx()
                    val audioManager =
                        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (locked) {
                            // v1.1.2 item 7b: a tap while locked shows
                            // the lock cluster (title + seekbar +
                            // unlock) together, NS1 v9.9 pattern.
                            // Drags are ignored. Changes are NOT
                            // consumed here, so the unlock button
                            // itself still receives its tap.
                            var moved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                if (!change.pressed) {
                                    if (!moved) {
                                        lockClusterVisible = true
                                        lockClusterTick++
                                    }
                                    return@awaitEachGesture
                                }
                                if ((change.position - down.position).getDistance() > touchSlop) moved = true
                            }
                        }
                        val startX = down.position.x
                        val startY = down.position.y
                        val startUptime = down.uptimeMillis
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        // Gestures starting on the top bar / bottom
                        // control strip belong to those child controls
                        // (back button, seekbar, CC/lock/fullscreen) —
                        // the video detector stays out of the way.
                        val topStripActive = controlsVisibleRef.value || locked
                        val bottomStripActive = controlsVisibleRef.value && !locked
                        if ((topStripActive && startY < topStripPx) ||
                            (bottomStripActive && startY > h - bottomStripPx)) {
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        // Middle band only for drags (item 5): a drag
                        // starting in the top/bottom 48dp is ignored —
                        // no volume, no brightness, no overlay. Taps
                        // still work anywhere on the video.
                        val inGestureBand =
                            startY >= topExclusionPx && startY <= h - bottomExclusionPx
                        var mode = DragMode.Pending
                        var seekBaseMs = session.positionMs.value
                        var seekTargetMs = seekBaseMs
                        var volBaseStep = volumeStepNow(audioManager)
                        var brightBaseStep = brightnessStepNow(activity?.window, context)
                        var finished = false
                        while (!finished) {
                            val elapsed = SystemClock.uptimeMillis() - startUptime
                            val remaining = longPressTimeout - elapsed
                            if (mode == DragMode.Pending && remaining <= 0) {
                                // Long-press deadline fired with no up
                                // and no drag: hold-to-2x until release.
                                prevSpeed = session.speed.value
                                session.setSpeed(2f)
                                holding2x = true
                                gestureLabel = "2× speed"
                                waitForUpOrCancellation()
                                session.setSpeed(prevSpeed)
                                holding2x = false
                                gestureLabel = null
                                finished = true
                                continue
                            }
                            val event = if (mode == DragMode.Pending && remaining > 0) {
                                withTimeoutOrNull(remaining) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }
                            // Timeout fired (null event): loop back and
                            // the deadline check above fires the 2x.
                            if (event == null) continue
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            // Up or cancel: a non-pressed change is exactly
                            // that (moves always arrive pressed).
                            if (!change.pressed) {
                                when (mode) {
                                    DragMode.Pending -> {
                                        // Tap (or the first tap of a
                                        // double-tap): wait for a second
                                        // down before deciding.
                                        val secondDown = withTimeoutOrNull(doubleTapTimeout) {
                                            awaitFirstDown()
                                        }
                                        if (secondDown == null) {
                                            controlsVisible = !controlsVisible
                                        } else {
                                            waitForUpOrCancellation()
                                            if (secondDown.position.x < w / 3f) {
                                                session.seekBy(-10_000)
                                                gestureLabel = "-10s"
                                            } else if (secondDown.position.x > 2f * w / 3f) {
                                                session.seekBy(10_000)
                                                gestureLabel = "+10s"
                                            } else {
                                                session.togglePlayPause()
                                            }
                                        }
                                    }
                                    DragMode.Seek -> {
                                        session.seekTo(seekTargetMs)
                                        gestureLabel = null
                                    }
                                    DragMode.Brightness, DragMode.Volume -> {
                                        // Overlay hides itself ~1200ms
                                        // after the last move (tick timer).
                                    }
                                    DragMode.Ignored -> { /* nothing */ }
                                }
                                finished = true
                                continue
                            }
                            if (!change.pressed) continue
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            if (mode == DragMode.Pending) {
                                if (maxOf(abs(dx), abs(dy)) < touchSlop) continue
                                if (!inGestureBand) {
                                    mode = DragMode.Ignored
                                    continue
                                }
                                // Direction-lock on first movement.
                                mode = if (abs(dx) > abs(dy)) DragMode.Seek
                                else if (startX < w / 2f) DragMode.Brightness else DragMode.Volume
                                change.consume()
                                continue
                            }
                            when (mode) {
                                DragMode.Seek -> {
                                    val total = durationMs.coerceAtLeast(1L)
                                    seekTargetMs = (seekBaseMs + dx / w * SWIPE_SEEK_FULL_WIDTH_MS)
                                        .toLong().coerceIn(0L, total)
                                    gestureLabel = fmtClock((seekTargetMs / 1000).toInt())
                                }
                                DragMode.Brightness -> {
                                    val window = activity?.window
                                    if (window != null) {
                                        val step = (brightBaseStep - dy / (h / GESTURE_STEPS))
                                            .roundToInt().coerceIn(0, GESTURE_STEPS)
                                        applyBrightnessStep(window, step)
                                        stepOverlay = StepOverlay(StepKind.Brightness, step)
                                        stepOverlayTick++
                                    }
                                }
                                DragMode.Volume -> {
                                    val step = (volBaseStep - dy / (h / GESTURE_STEPS))
                                        .roundToInt().coerceIn(0, GESTURE_STEPS)
                                    applyVolumeStep(audioManager, step)
                                    stepOverlay = StepOverlay(StepKind.Volume, step)
                                    stepOverlayTick++
                                }
                                else -> { /* Pending / Ignored: nothing */ }
                            }
                            change.consume()
                        }
                    }
                }
        ) {
            // v1.1.2 item 3: the 68dp pause circle must never touch
            // the seekbar. The bottom controls are ~107dp tall and
            // the seekbar track sits ~19dp into them, so the circle's
            // bottom must clear (maxHeight - 92dp). Shift the centre
            // row up only as much as needed — fullscreen (tall)
            // needs none, portrait 16:9 needs ~16dp.
            val centerShiftUp = (126.dp - maxHeight / 2).coerceAtLeast(0.dp)
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
            // v1.1.1 item 2: the 15-step volume/brightness overlay wins
            // over the text pill while it is up (0-15, 15 = 100%).
            val overlay = stepOverlay
            if (overlay != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        if (overlay.kind == StepKind.Volume) NsIcons.Volume else NsIcons.Brightness,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(GESTURE_STEPS) { i ->
                            Box(
                                modifier = Modifier.size(width = 5.dp, height = 18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (i < overlay.step) NsColors.AccentBright
                                        else Color.White.copy(alpha = 0.25f)
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${overlay.step} / $GESTURE_STEPS",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            } else if (gestureLabel != null) {
                Box(
                    Modifier.align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(gestureLabel!!, color = Color.White)
                }
            }
            // Top bar: back (X) + title. Locked: it belongs to the
            // lock cluster — title + unlock button show TOGETHER
            // (v1.1.2 item 7b, NS1 v9.9 pattern).
            if (controlsVisible || lockClusterVisible) {
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
                    if (locked) {
                        IconButton(
                            onClick = {
                                locked = false
                                lockClusterVisible = false
                                controlsVisible = true
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(NsIcons.Lock, contentDescription = "Unlock controls", tint = Color.White, modifier = Modifier.size(21.dp))
                        }
                    }
                }
            }
            // Centre controls. v1.1.3 item 1: prev/next episode sit
            // HERE, flanking play/pause (YouTube-style) — Sam's order,
            // NOT in the bottom row. Layout: skip 52dp + 16dp gap +
            // prev 48dp + 16dp + play 68dp + 16dp + next 48dp + 16dp +
            // skip 52dp = 332dp total, fits Sam's ~393dp phone with
            // room to spare. v1.1.2 item 3: shifted up just enough
            // that the 68dp pause circle never touches the seekbar
            // (portrait 16:9); fullscreen needs no shift.
            if (controlsVisible && !locked) {
                Row(
                    modifier = Modifier.align(Alignment.Center).offset(y = -centerShiftUp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                    // v1.1.3 item 1: previous episode, next to
                    // play/pause — moved here from the bottom row.
                    IconButton(
                        onClick = { session.previous() },
                        enabled = index > 0,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            NsIcons.Previous,
                            contentDescription = "Previous episode",
                            tint = if (index > 0) Color.White else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(26.dp)
                        )
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
                    // v1.1.3 item 1: next episode, next to play/pause
                    // — moved here from the bottom row.
                    IconButton(
                        onClick = { session.next() },
                        enabled = index < playlistSize - 1,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            NsIcons.Next,
                            contentDescription = "Next episode",
                            tint = if (index < playlistSize - 1) Color.White else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(26.dp)
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
                    // v1.1.2 item 7a: reserved preview strip — the
                    // scrub-time bubble lives here while dragging, so
                    // the layout never shifts under the finger.
                    BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)) {
                        val preview = seekPreviewMs
                        if (preview != null && durationMs > 0) {
                            val frac = (preview.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .offset(x = ((maxWidth * frac) - 26.dp).coerceIn(0.dp, maxWidth - 52.dp))
                                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    fmtClock((preview / 1000).toInt()),
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    // v1.1.2 items 6/7a: drag the seekbar to scrub —
                    // the target time shows FIRST (bubble + dot move
                    // to the preview position) and the seek commits
                    // only on release, then buffers. A tap still
                    // jumps immediately.
                    val shownFrac = seekPreviewMs?.let {
                        (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } ?: progress
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .pointerInput(durationMs) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        if (durationMs > 0) {
                                            val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                            seekPreviewMs = (frac * durationMs).toLong()
                                        }
                                    },
                                    onDragEnd = {
                                        val target = seekPreviewMs
                                        seekPreviewMs = null
                                        if (target != null) session.seekTo(target)
                                    },
                                    onDragCancel = { seekPreviewMs = null },
                                    onHorizontalDrag = { change, _ ->
                                        if (durationMs > 0) {
                                            val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                            seekPreviewMs = (frac * durationMs).toLong()
                                        }
                                        change.consume()
                                    }
                                )
                            }
                    ) {
                        SeekbarVisual(
                            progressFrac = shownFrac,
                            bufferedFrac = buffered,
                            trackModifier = Modifier.pointerInput(durationMs) {
                                detectTapGestures { offset ->
                                    if (durationMs > 0) {
                                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        session.seekTo((frac * durationMs).toLong())
                                    }
                                }
                            }
                        )
                    }
                    PlayerTimeRow(positionSec, remainingSec, durationSec)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // v1.1.3 item 1: prev/next live in the centre
                        // cluster now (next to play/pause), NOT here.
                        // Bottom row = CC / quality / lock / fullscreen.
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
                        // v1.1.2 item 5: quality badge opens the
                        // quality picker — this episode's copies, best
                        // first; tap one to switch to it. The badge
                        // always shows the quality ACTUALLY playing.
                        // (Each Telegram quality is a separate whole
                        // file, no ABR.)
                        Box(
                            modifier = Modifier
                                .border(1.6.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                                .clickable(enabled = item != null) {
                                    qualityPickerOpen = true
                                    if (pickerCopies == null) loadPickerCopies()
                                },
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
                        IconButton(
                            onClick = {
                                locked = true
                                lockClusterVisible = true
                                lockClusterTick++
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
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
            // v1.1.2 item 7b: lock cluster, bottom half — the seekbar
            // + time row, dimmed and dead (no gestures), shown TOGETHER
            // with the top bar (title + unlock) on the same 3s timer.
            // NS1 v9.9 pattern: no toast, cluster moves as one.
            if (locked && lockClusterVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .alpha(0.5f)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 8.dp)
                ) {
                    SeekbarVisual(progressFrac = progress, bufferedFrac = buffered)
                    PlayerTimeRow(positionSec, remainingSec, durationSec)
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
                        // v1.1.3 item 2: Up Next is a HORIZONTAL rail
                        // in the mini (portrait) player — swipe through
                        // the next episodes like YouTube. Same big-card
                        // look as v1.1.2 (16/9 thumb, name below), but
                        // sized for the rail (250dp wide, so the next
                        // card peeks in). Real data, deduped playlist
                        // order; tap plays that episode. Fewer than 10
                        // remaining = fewer cards, never fake rows.
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(upNextRail) { offset, upItem ->
                                Column(
                                    modifier = Modifier
                                        .width(250.dp)
                                        .clickable { session.playAt(index + 1 + offset) }
                                ) {
                                    VideoThumb(
                                        repo = repo,
                                        item = upItem,
                                        showBadges = false,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                    )
                                    Text(
                                        upItem.title,
                                        fontFamily = ManropeFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = NsColors.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 9.dp)
                                    )
                                    Text(
                                        listOfNotNull(
                                            upItem.quality.label.takeIf { upItem.quality.label != "Unknown" },
                                            FormatUtils.formatDuration(upItem.durationSec).takeIf { upItem.durationSec > 0 },
                                            FormatUtils.formatSize(upItem.sizeBytes).takeIf { upItem.sizeBytes > 0 }
                                        ).joinToString(" · "),
                                        fontFamily = ManropeFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
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
        // v1.1.2 item 5: quality picker — this episode's copies,
        // best first; tap one to switch playback to it (position
        // kept). Reuses the v1.1.1 CopiesSheet for the list itself.
        if (qualityPickerOpen) {
            val copies = pickerCopies
            if (copies != null) {
                CopiesSheet(
                    copies = copies,
                    onPick = { copy ->
                        qualityPickerOpen = false
                        session.playCopy(copy)
                    },
                    onDismiss = { qualityPickerOpen = false }
                )
            } else {
                QualityPickerStateSheet(
                    loading = pickerLoading,
                    onRetry = { loadPickerCopies() },
                    onDismiss = { qualityPickerOpen = false }
                )
            }
        }
    }
}

/**
 * v1.1.1 items 2/4/5: one gesture detector owns the whole video area.
 * Horizontal drag = seek (direction-locked), vertical drag = brightness
 * (left half) / volume (right half) on a 0-15 scale, top+bottom 48dp
 * bands excluded from drags. Taps, double-taps and long-press (2x)
 * live in the same detector so a drag can never be misread as a
 * long-press (the v1.1.0 two-detector bug).
 */
private enum class DragMode { Pending, Seek, Brightness, Volume, Ignored }

private enum class StepKind { Volume, Brightness }

/** v1.1.1 item 2: the 15-step overlay state (0-15, 15 = 100%). */
private data class StepOverlay(val kind: StepKind, val step: Int)

/** Full-width horizontal swipe = 60s of seek (NobiStream 1 v9.5 constant). */
private const val SWIPE_SEEK_FULL_WIDTH_MS = 60_000L

/** v1.1.1 item 5: notification-shade + gesture-nav exclusion bands. */
private const val TOP_EDGE_GESTURE_EXCLUSION_DP = 48
private const val BOTTOM_EDGE_GESTURE_EXCLUSION_DP = 48

/** v1.1.1 item 2: volume/brightness steps — 15 steps, 15 = 100%. */
private const val GESTURE_STEPS = 15

private fun volumeStepNow(am: AudioManager): Int {
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    return ((cur * GESTURE_STEPS.toFloat()) / max).roundToInt().coerceIn(0, GESTURE_STEPS)
}

private fun applyVolumeStep(am: AudioManager, step: Int) {
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val vol = ((step * max) / GESTURE_STEPS.toFloat()).roundToInt().coerceIn(0, max)
    am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
}

private fun brightnessStepNow(window: Window?, context: Context): Int {
    val lp = window?.attributes?.screenBrightness ?: -1f
    if (lp >= 0f) return (lp * GESTURE_STEPS).roundToInt().coerceIn(0, GESTURE_STEPS)
    val sys = Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS,
        128
    )
    return ((sys * GESTURE_STEPS.toFloat()) / 255f).roundToInt().coerceIn(0, GESTURE_STEPS)
}

private fun applyBrightnessStep(window: Window, step: Int) {
    val lp = window.attributes
    // Step 0 = a 0.02 floor so the screen never goes fully black.
    lp.screenBrightness = if (step <= 0) 0.02f else (step / GESTURE_STEPS.toFloat()).coerceIn(0.02f, 1f)
    window.attributes = lp
}

private fun fmtClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
    else "%02d:%02d".format(m, sec)
}

/**
 * v1.1.2 item 6: three-colour seek track — played (accent-bright),
 * buffered-but-not-played (translucent accent), remaining (dark).
 * The buffered segment comes from the player's real
 * bufferedPercentage; it never fakes progress.
 */
@Composable
private fun SeekTrack(progressFrac: Float, bufferedFrac: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.28f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(bufferedFrac.coerceIn(0f, 1f))
                .height(4.5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(NsColors.AccentBright.copy(alpha = 0.45f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progressFrac.coerceIn(0f, 1f))
                .height(4.5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(NsColors.AccentBright)
        )
    }
}

/**
 * v1.1.2 items 6/7: the seekbar visual only (track + dot), no
 * gestures — used by the interactive seekbar and by the locked
 * (dimmed, dead) seekbar alike. [trackModifier] lets the caller
 * attach the tap detector to the track itself.
 */
@Composable
private fun SeekbarVisual(
    progressFrac: Float,
    bufferedFrac: Float,
    trackModifier: Modifier = Modifier
) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp)) {
        SeekTrack(
            progressFrac = progressFrac,
            bufferedFrac = bufferedFrac,
            modifier = Modifier.align(Alignment.Center).then(trackModifier)
        )
        Box(
            modifier = Modifier
                .offset(x = ((maxWidth * progressFrac.coerceIn(0f, 1f)) - 7.dp).coerceAtLeast(0.dp))
                .align(Alignment.CenterStart)
                .size(14.dp)
                .clip(CircleShape)
                .background(NsColors.AccentBright)
        )
    }
}

/** Time row under the seekbar: elapsed left, remaining · total right. */
@Composable
private fun PlayerTimeRow(positionSec: Int, remainingSec: Int, durationSec: Int) {
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
}

/**
 * v1.1.2 item 5: the quality picker's loading / error state. The
 * success state reuses the v1.1.1 CopiesSheet directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityPickerStateSheet(
    loading: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Choose quality",
                fontFamily = SoraFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = NsColors.TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            if (loading) {
                CircularProgressIndicator(color = NsColors.AccentBright)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Loading copies…",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = NsColors.TextSecondary
                )
            } else {
                Text(
                    "Copies couldn't be loaded. Check your connection and try again.",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = NsColors.TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(NsColors.Primary.copy(alpha = 0.14f))
                        .clickable { onRetry() }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Retry",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = NsColors.AccentBright
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * v1.1.2 item 5: load this episode's copies for the quality picker.
 * Pages the channel list (TDLib, local DB — fast) until the group
 * containing [item] is found, then one extra page to catch
 * stragglers. Returns the copies best-first, or null when the group
 * could not be found / loading failed.
 */
private suspend fun loadCopiesFor(repo: NobiRepository, chatId: Long, item: VideoItem): List<VideoItem>? {
    val collected = mutableListOf<VideoItem>()
    var fromId = 0L
    var pages = 0
    while (pages < 12) {
        pages++
        val page = runCatching { repo.loadVideos(chatId, fromId, 50) }.getOrNull() ?: return null
        collected.addAll(page.videos)
        val groups = SmartFilter.groupEpisodes(collected)
        val group = groups.firstOrNull { g ->
            g.copies.any { it.messageId == item.messageId && it.chatId == item.chatId }
        }
        if (group != null) {
            if (page.rawCount >= 50 && page.lastMessageId > 0L && pages < 12) {
                runCatching { repo.loadVideos(chatId, page.lastMessageId, 50) }
                    .getOrNull()?.let { collected.addAll(it.videos) }
            }
            return SmartFilter.groupEpisodes(collected)
                .firstOrNull { g ->
                    g.copies.any { it.messageId == item.messageId && it.chatId == item.chatId }
                }?.copies
        }
        if (page.rawCount < 50 || page.lastMessageId <= 0L) break
        fromId = page.lastMessageId
    }
    return null
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
