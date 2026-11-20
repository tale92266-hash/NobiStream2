package com.nobi.nobistream2.ui.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.nobi.nobistream2.core.SmartFilter
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.data.NobiRepository
import com.nobi.nobistream2.data.SubtitleTrack
import com.nobi.nobistream2.playback.PlayerPlaylist
import com.nobi.nobistream2.playback.TdlibDataSource
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application-scoped playback session (owned by NobiStream2Application).
 * One ExoPlayer for the whole app, so the mini-player keeps playing while
 * the user navigates, and Next/Previous follow the filtered playlist the
 * user was looking at.
 *
 * Playback rules carried over from the old PlayerActivity (verified):
 * - Silent auto-resume: setMediaItem(item, savedPosition) + prepare() in
 *   ONE call, never prepare-at-0-then-seek (that was the v5 resume bug).
 * - A stall (IOException / IO error code) shows the spinner + background
 *   retry with backoff, up to 10 consecutive failures; only then an
 *   error screen. Decoder/parse failures are fatal at once.
 * - No ABR quality switching: each Telegram quality is a separate whole
 *   file; Smart Filter picks the best copy per episode instead.
 * - Buffer targets 50s min / 120s max, 1s start, 2s rebuffer, 30s back buffer.
 */
@OptIn(UnstableApi::class)
class PlayerSession(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var repository: NobiRepository? = null
    private var gateway: com.nobi.nobistream2.data.TelegramGateway? = null

    var player: ExoPlayer? = null
        private set

    private val _current = MutableStateFlow<VideoItem?>(null)
    val current: StateFlow<VideoItem?> = _current.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _playlistSize = MutableStateFlow(0)
    val playlistSize: StateFlow<Int> = _playlistSize.asStateFlow()
    private val _playlistIndex = MutableStateFlow(0)
    val playlistIndex: StateFlow<Int> = _playlistIndex.asStateFlow()
    private val _autoplay = MutableStateFlow(true)
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()
    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks.asStateFlow()
    private val _subtitlesEnabled = MutableStateFlow(false)
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()
    private val _sleepMinutesLeft = MutableStateFlow(0)
    val sleepMinutesLeft: StateFlow<Int> = _sleepMinutesLeft.asStateFlow()

    var playlist: List<VideoItem> = emptyList()
        private set
    /** When set, the current item plays from this local file, not TDLib. */
    var downloadLocalPath: String? = null
        private set

    /** Play a completed download from its local file. */
    fun openDownloaded(item: VideoItem, localPath: String) {
        ensurePlayer()
        downloadLocalPath = localPath
        PlayerPlaylist.items = listOf(item)
        PlayerPlaylist.chatId = item.chatId
        PlayerPlaylist.fromContinue = false
        playlist = listOf(item)
        chatId = item.chatId
        _playlistSize.value = 1
        playAt(0, firstLoad = true)
    }
    var chatId: Long = 0L
        private set
    private var index: Int = 0
    private var stallRetryCount = 0
    private var stallJob: Job? = null
    private var saveJob: Job? = null
    private var positionJob: Job? = null
    private var sleepJob: Job? = null
    private var subtitleJob: Job? = null
    private var currentItemUnresolvable = false
    private var attachedSubtitleMessageId: Long = -1L

    fun attachRepository(repo: NobiRepository, gw: com.nobi.nobistream2.data.TelegramGateway) {
        repository = repo
        gateway = gw
        scope.launch {
            _autoplay.value = repo.settingsStore.autoplay.first()
        }
    }

    fun setAutoplay(value: Boolean) {
        _autoplay.value = value
        scope.launch { repository?.settingsStore?.setAutoplay(value) }
    }

    fun setSpeed(value: Float) {
        _speed.value = value
        player?.setPlaybackSpeed(value)
    }

    fun ensurePlayer() {
        if (player != null) return
        val gw = gateway ?: return
        val factory = TdlibDataSource.Factory(gw)
        val mediaSourceFactory = ProgressiveMediaSource.Factory(factory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(50_000, 120_000, 1_000, 2_000)
            .setBackBuffer(30_000, true)
            .build()
        player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { exo ->
                exo.addListener(playerListener)
                exo.setPlaybackSpeed(_speed.value)
            }
    }

    /** Media3 data source factory needs the gateway directly. */
    fun open(items: List<VideoItem>, startIndex: Int, chatId: Long, fromContinue: Boolean = false) {
        downloadLocalPath = null
        ensurePlayer()
        PlayerPlaylist.items = items
        PlayerPlaylist.chatId = chatId
        PlayerPlaylist.fromContinue = fromContinue
        playlist = items
        this.chatId = chatId
        _playlistSize.value = items.size
        playAt(startIndex, firstLoad = true)
        if (fromContinue) maybeExpandContinuePlaylist()
    }

    fun playAt(newIndex: Int, firstLoad: Boolean = false) {
        val exo = player ?: return
        if (newIndex < 0 || newIndex >= playlist.size) return
        savePositionNow()
        index = newIndex
        _playlistIndex.value = newIndex
        val item = playlist[newIndex]
        _current.value = item
        _error.value = null
        stallJob?.cancel()
        stallRetryCount = 0
        currentItemUnresolvable = false
        subtitleJob?.cancel()
        _subtitleTracks.value = emptyList()
        _subtitlesEnabled.value = false
        attachedSubtitleMessageId = -1L
        // Downloaded file: no TDLib resolve needed, play the local file.
        if (downloadLocalPath != null && index == 0 && playlist.size == 1) {
            val repo0 = repository
            scope.launch(Dispatchers.IO) {
                val saved = repo0?.getPositionSec(item.chatId, item.messageId) ?: 0L
                withContext(Dispatchers.Main) {
                    if (player == null) return@withContext
                    val resumable = saved > 5L && item.durationSec > 0 && saved < item.durationSec - 10L
                    val targetMs = if (resumable) saved.coerceIn(0L, item.durationSec * 1000L - 2_000L) * 1000L else 0L
                    val mediaItem = MediaItem.Builder().setUri(Uri.fromFile(File(downloadLocalPath!!))).build()
                    exo.setMediaItem(mediaItem, targetMs)
                    exo.prepare()
                    exo.play()
                }
            }
            return
        }
        scope.launch(Dispatchers.IO) {
            val repo = repository ?: return@launch
            val detailed = runCatching { repo.resolveVideoItemDetailed(item) }.getOrNull()
            val resolved = detailed?.item ?: item
            val saved = repo.getPositionSec(resolved.chatId, resolved.messageId)
            withContext(Dispatchers.Main) {
                if (index != newIndex || player == null) return@withContext
                currentItemUnresolvable = detailed?.freshIdResolved == false
                val resumable = saved > 5L && resolved.durationSec > 0 &&
                    saved < resolved.durationSec - 10L
                val targetMs = if (resumable) {
                    saved.coerceIn(0L, resolved.durationSec * 1000L - 2_000L) * 1000L
                } else {
                    0L
                }
                val mediaItem = MediaItem.Builder().setUri("tdlib://${resolved.fileId}").build()
                exo.setMediaItem(mediaItem, targetMs)
                exo.prepare()
                exo.play()
                startSubtitleDiscovery(resolved)
            }
        }
    }

    fun next() = playAt(index + 1)
    fun previous() = playAt(index - 1)
    fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seekTo(ms: Long) {
        val exo = player ?: return
        exo.seekTo(ms.coerceIn(0L, exo.duration.coerceAtLeast(0L)))
        savePositionNow()
    }

    fun seekBy(deltaMs: Long) {
        val exo = player ?: return
        seekTo(exo.currentPosition + deltaMs)
    }

    fun retry() {
        _error.value = null
        stallRetryCount = 0
        playAt(index)
    }

    fun stopAndSave() {
        savePositionNow()
        player?.pause()
    }

    fun release() {
        savePositionNow()
        saveJob?.cancel()
        positionJob?.cancel()
        player?.release()
        player = null
    }

    /** Sleep timer: stops playback after [minutes]; 0 cancels. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        _sleepMinutesLeft.value = minutes
        if (minutes <= 0) return
        scope.launch { repository?.settingsStore?.setSleepTimerMin(minutes) }
        sleepJob = scope.launch {
            var left = minutes
            while (left > 0) {
                delay(60_000L)
                left--
                _sleepMinutesLeft.value = left
            }
            player?.pause()
            _sleepMinutesLeft.value = 0
        }
    }

    fun setSubtitlesEnabled(enabled: Boolean, language: String? = null) {
        val exo = player ?: return
        _subtitlesEnabled.value = enabled
        val params = exo.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage(if (enabled) language else null)
            .build()
        exo.trackSelectionParameters = params
    }

    private fun startSubtitleDiscovery(item: VideoItem) {
        val repo = repository ?: return
        subtitleJob = scope.launch(Dispatchers.IO) {
            val tracks = runCatching { repo.findSubtitles(item) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                if (index >= playlist.size || playlist[index].messageId != item.messageId) return@withContext
                _subtitleTracks.value = tracks
                if (tracks.isNotEmpty()) {
                    val always = runCatching { repo.settingsStore.subtitleAlwaysShow.first() }.getOrNull() ?: false
                    if (always) setSubtitlesEnabled(true, tracks.first().language)
                    // Attach tracks: Media3 needs one re-prepare at the same position.
                    val exo = player ?: return@withContext
                    val pos = exo.currentPosition
                    val wasPlaying = exo.isPlaying
                    val builder = MediaItem.Builder().setUri("tdlib://${item.fileId}")
                    builder.setSubtitleConfigurations(tracks.mapIndexed { i, track ->
                        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(track.localPath)))
                            .setMimeType(if (track.isVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage(track.language)
                            .setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0)
                            .build()
                    })
                    attachedSubtitleMessageId = item.messageId
                    exo.setMediaItem(builder.build(), pos)
                    exo.prepare()
                    if (wasPlaying) exo.play()
                }
            }
        }
    }

    private fun maybeExpandContinuePlaylist() {
        if (!PlayerPlaylist.fromContinue || chatId == 0L) return
        val repo = repository ?: return
        val currentId = playlist.getOrNull(index)?.messageId ?: return
        scope.launch(Dispatchers.IO) {
            val lastChat = runCatching { repo.settingsStore.lastChatId.first() }.getOrNull() ?: 0L
            val filter = if (lastChat == chatId) {
                runCatching { repo.settingsStore.lastFilter.first() }.getOrNull() ?: "All"
            } else "All"
            val collected = mutableListOf<VideoItem>()
            var fromId = 0L
            var found = false
            var pages = 0
            while (!found && pages < 5) {
                pages++
                val page = runCatching { repo.loadVideos(chatId, fromId, 50) }.getOrNull() ?: break
                collected.addAll(page.videos)
                if (page.videos.any { it.messageId == currentId }) found = true
                if (page.rawCount < 50 || page.lastMessageId <= 0L) break
                fromId = page.lastMessageId
            }
            if (!found) return@launch
            val smart = runCatching { repo.settingsStore.smartFilter.first() }.getOrNull() ?: true
            var finalList = if (smart) SmartFilter.dedup(collected) else collected
            if (filter != "All") finalList = finalList.filter { it.quality.label == filter }
            val newIndex = finalList.indexOfFirst { it.messageId == currentId }
            if (newIndex < 0) return@launch
            withContext(Dispatchers.Main) {
                if (playlist.getOrNull(index)?.messageId != currentId) return@withContext
                playlist = finalList
                PlayerPlaylist.items = finalList
                index = newIndex
                _playlistIndex.value = newIndex
                _playlistSize.value = finalList.size
            }
        }
    }

    private fun savePositionNow() {
        val exo = player ?: return
        val item = _current.value ?: return
        val repo = repository ?: return
        val posSec = (exo.currentPosition / 1000L).coerceAtLeast(0L)
        if (exo.duration > 0 && posSec <= 0L) return
        scope.launch(Dispatchers.IO) {
            runCatching { repo.savePosition(item, posSec) }
        }
    }

    private fun startSaveTicker() {
        saveJob?.cancel()
        positionJob?.cancel()
        saveJob = scope.launch {
            while (true) {
                delay(5_000L)
                savePositionNow()
            }
        }
        positionJob = scope.launch {
            while (true) {
                val exo = player
                _positionMs.value = exo?.currentPosition ?: 0L
                _durationMs.value = exo?.duration?.coerceAtLeast(0L) ?: 0L
                delay(500L)
            }
        }
    }

    private fun stopSaveTicker() {
        saveJob?.cancel()
        positionJob?.cancel()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> _isBuffering.value = true
                Player.STATE_READY -> {
                    _isBuffering.value = false
                    _error.value = null
                    stallRetryCount = 0
                    stallJob?.cancel()
                }
                Player.STATE_ENDED -> {
                    _isBuffering.value = false
                    savePositionNow()
                    if (_autoplay.value) playAt(index + 1)
                }
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) startSaveTicker() else stopSaveTicker()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) savePositionNow()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlayerSession", "Playback failed for ${_current.value?.title}", error)
            if (!currentItemUnresolvable && isStallError(error)) {
                stallRetryCount++
                if (stallRetryCount >= 10) {
                    _error.value = "Connection lost — Tap to retry"
                } else {
                    _isBuffering.value = true
                    stallJob?.cancel()
                    stallJob = scope.launch {
                        delay((1_000L * stallRetryCount).coerceAtMost(8_000L))
                        player?.prepare()
                        player?.play()
                    }
                }
                return
            }
            _error.value = "Couldn't play this video"
        }
    }

    private fun isStallError(error: PlaybackException): Boolean {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is java.io.IOException) return true
            cause = cause.cause
        }
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
    }
}
