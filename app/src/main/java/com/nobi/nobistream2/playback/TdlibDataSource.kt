package com.nobi.nobistream2.playback

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.nobi.nobistream2.data.TelegramGateway
import com.nobi.nobistream2.data.VideoFileInfo
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * ExoPlayer data source that streams a Telegram video through TDLib.
 *
 * The media URI is `tdlib://<fileId>`. On open, the source asks TDLib for
 * the local file (starting its download when needed). On read, if the
 * requested bytes are not downloaded yet, it downloads that byte range
 * from Telegram and then reads it from the local file. Seeking just
 * re-opens at a new position; already downloaded ranges are reused from
 * TDLib's file cache in filesDir (useFileDatabase is on), so a seek never
 * re-downloads what is there.
 *
 * v5 seek fix — the v4 code threw after 3 immediate attempts when a
 * range wasn't downloaded yet, so a +10s seek (whose bytes simply
 * hadn't arrived) showed the playback-error screen instead of
 * buffering. Now ensureDownloaded() waits patiently (up to 60s) for
 * the range to arrive, proving coverage from the returned state on
 * every attempt — a range that still isn't proven after 60s throws,
 * so the error screen with Retry remains for genuinely stuck
 * downloads. Garbage is still never served: only proven ranges are
 * read.
 *
 * v7 seek/resume LATENCY fix — the v6 code was correct but SLOW.
 * Measured from the code (Sam, 2026-11-17: resume and swipe-forward
 * seek "bhot load leta hai" on v6, confirmed on 2.3.0): every open()
 * at a seek target BLOCKED on a synchronous 8 MB range download
 * before ExoPlayer could even start the extractor, and a resume
 * paid for it TWICE — Media3 first loads at offset 0 (it must read
 * the moov to map the resume time to a byte offset), where the first
 * read blocked on another synchronous 8 MB, and only then seeks to
 * the target, where open() blocked on 8 MB more. ~16 MB of blocking
 * download before the first frame: ~16 s at 1 MB/s, ~32 s at
 * 500 KB/s. Each 8 MB boundary crossed while playing blocked the
 * loader thread the same way, because nothing prefetched in the
 * background. The fix:
 *  - open() blocks ONLY on a 1 MB seed (moov + first samples — all
 *    the extractor needs to reach STATE_READY). The 8 MB lookahead
 *    is now a BACKGROUND prefetch (DownloadFile, synchronous =
 *    false): bytes keep arriving while the player starts.
 *  - ensureDownloaded() blocks ONLY until the exact read range is
 *    proven — never the whole 8 MB. It polls TDLib's cached file
 *    state (free, no TDLib call) every 100 ms, and only re-issues a
 *    small blocking fetch if no progress shows for 2 s.
 *  - every read extends the background prefetch window when the
 *    read position gets within 4 MB of its end, so the download
 *    stays 4–12 MB ahead and crossing an 8 MB boundary never
 *    blocks.
 *  - the v6 single high-water mark (prefetchedUntil) is replaced by
 *    a PROVEN-INTERVAL SET: a high-water mark is unsound once
 *    ranges are downloaded out of order (seek to 100 MB, then back
 *    to 50 MB — the mark says 108 MB, but 50 MB was never
 *    downloaded, and the old check would have served garbage).
 *    Only intervals a returned file state proved are recorded, and
 *    only those skip a download.
 * The remaining floor is the network itself: ~2 MB of blocking
 * download on resume (~1 MB at 0 + ~1 MB at the target) instead of
 * ~16 MB, so the wait is ~8x shorter at ANY Telegram speed. If
 * Telegram itself is slow, the first frame still waits for that
 * 2 MB — that is the honest floor, not a bug.
 */
@UnstableApi
class TdlibDataSource(
    private val gateway: TelegramGateway
) : BaseDataSource(false) {

    private var fileId: Int = 0
    private var fileInfo: VideoFileInfo? = null
    private var randomAccessFile: RandomAccessFile? = null
    private var readPosition: Long = 0L
    private var bytesRemaining: Long = 0L
    private var opened = false
    private var currentUri: Uri? = null

    companion object {
        /**
         * v7: how much ONE blocking fetch waits for. 1 MB covers the
         * moov of a typical episode + the first samples, so the
         * extractor reaches STATE_READY after ~1 MB instead of 8 MB.
         * A read that needs more (a big moov) still blocks on its
         * own exact length — see ensureDownloaded().
         */
        private const val INITIAL_BYTES = 1L * 1024L * 1024L

        /**
         * v7: background prefetch window. One window extension asks
         * TDLib (non-blocking) for this much more, so the download
         * runs ~8 MB ahead of what the player has read.
         */
        private const val PREFETCH_CHUNK_BYTES = 8L * 1024L * 1024L

        /**
         * v7: extend the prefetch window when the read position gets
         * within this much of its end — the next 8 MB is already in
         * flight before the player reaches the boundary.
         */
        private const val PREFETCH_TRIGGER_BYTES = 4L * 1024L * 1024L

        /**
         * v5 seek fix (unchanged): how long a read may WAIT for its
         * bytes to arrive before it is allowed to fail. A +10s seek
         * lands on a range that is simply not downloaded yet — that
         * is buffering, not failure, and the player shows its
         * buffering overlay while this loop waits.
         */
        private const val MAX_ENSURE_WAIT_MS = 60_000L

        /** v7: how often the wait loop re-checks the cached state. */
        private const val ENSURE_POLL_MS = 100L

        /**
         * v7: if the cached state shows no progress for this long,
         * re-issue one small blocking fetch to force the download
         * forward (a synchronous DownloadFile can return early when
         * superseded — that proves nothing and must be retried, but
         * not on every 100 ms poll).
         */
        private const val FORCE_RETRY_MS = 2_000L
    }

    /**
     * v7: byte intervals [start, end) PROVEN downloaded for the
     * current file — only ever added from a file state that proves
     * them (see addProven). Merged, non-overlapping, sorted by
     * insertion order of the merge (order is irrelevant; membership
     * is what matters). Reset only when a DIFFERENT video opens, so
     * a seek back to an already-downloaded spot is instant.
     */
    private val provenRanges = mutableListOf<Pair<Long, Long>>()

    /**
     * v7: end of the furthest range a background prefetch has been
     * ASKED for (not proven — asking is not proof). Re-aimed at
     * every open(), because each open (initial load or seek) starts
     * reading at a new position.
     */
    private var prefetchRequestedUntil: Long = 0L

    override fun getUri(): Uri? = currentUri

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        currentUri = dataSpec.uri
        val newFileId = dataSpec.uri.host?.toIntOrNull()
            ?: dataSpec.uri.lastPathSegment?.toIntOrNull()
            ?: throw IllegalArgumentException("Bad tdlib URI: ${dataSpec.uri}")
        if (newFileId != fileId) {
            // A different video: previous proof bookkeeping is void.
            provenRanges.clear()
        }
        fileId = newFileId
        val position = dataSpec.position
        // Every open (initial load AND every seek) re-aims the
        // prefetch window at the new read position: the old window
        // belonged to the old position and must not suppress a
        // prefetch for this one.
        prefetchRequestedUntil = 0L
        val info = runBlocking {
            val cached = gateway.getCachedFileState(fileId)
            val seed = seedBytes(cached?.sizeBytes ?: 0L, position)
            // v7 fast path: TDLib's cached state already proves the
            // first chunk (repeat open, or a seek back to a
            // downloaded spot) — no DownloadFile call at all, the
            // file just opens. v6 always issued a synchronous
            // DownloadFile here, even for bytes already on disk.
            var found: VideoFileInfo? = cached
                ?.takeIf { state ->
                    state.localPath.isNotEmpty() &&
                        File(state.localPath).exists() &&
                        (isProven(position, seed) || covers(state, position, seed))
                }?.also { addProven(it) }
            if (found == null) {
                // Start the background prefetch FIRST (8 MB ahead,
                // non-blocking): bytes keep arriving while the
                // player starts from the small chunk below.
                maybePrefetch(position)
                // v7: block ONLY on the first 1 MB (moov + first
                // samples), not the whole 8 MB lookahead. The
                // extractor needs the header + ~1 s of media to
                // reach STATE_READY; the rest arrives via the
                // prefetch above. The old code fetched 8 MB here —
                // the whole resume/seek wait.
                found = runCatching {
                    gateway.ensureRange(fileId, position, seed)
                }.getOrNull()
                    ?.takeIf { range ->
                        range.localPath.isNotEmpty() && File(range.localPath).exists()
                    }?.also { addProven(it) }
            }
            if (found == null) {
                // Unchanged fallback: wait for the local file to
                // exist at all (a path can be assigned before the
                // file exists on disk — opening it would throw
                // FileNotFoundException straight into the error
                // screen, so wait for the real file).
                for (attempt in 0 until 60) {
                    val candidate = gateway.downloadFileInfo(fileId)
                    if (candidate != null && File(candidate.localPath).exists()) {
                        found = candidate
                        break
                    }
                    delay(500L)
                }
            }
            // Whatever path produced the file, keep the download
            // running ahead of this position in the background.
            if (found != null) maybePrefetch(position)
            found
        } ?: throw java.io.IOException("Could not start download of video $fileId")
        fileInfo = info
        readPosition = dataSpec.position
        val totalSize = info.sizeBytes
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            totalSize > 0L -> totalSize - dataSpec.position
            else -> C.LENGTH_UNSET.toLong()
        }
        randomAccessFile = RandomAccessFile(File(info.localPath), "r").apply {
            seek(readPosition)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        if (toRead <= 0) return C.RESULT_END_OF_INPUT
        ensureDownloaded(readPosition, toRead.toLong())
        val raf = randomAccessFile ?: return C.RESULT_END_OF_INPUT
        val read = raf.read(buffer, offset, toRead)
        if (read == -1) return C.RESULT_END_OF_INPUT
        readPosition += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        // v7: keep the download ahead of the read position. The
        // plain check first so the common case (window already
        // ahead) costs no runBlocking at all; only when the read
        // position is within 4 MB of the window end does the next
        // 8 MB get asked for — already in flight before the player
        // reaches it, so crossing a boundary never blocks.
        if (readPosition + PREFETCH_TRIGGER_BYTES > prefetchRequestedUntil) {
            runBlocking { maybePrefetch(readPosition) }
        }
        return read
    }

    /**
     * Block until the requested range is downloaded — or throw.
     *
     * NEVER serve bytes that aren't proven downloaded: the old code
     * gave up after 3 attempts and read the region anyway, and
     * TDLib's local file can hold garbage in not-yet-downloaded
     * parts. An IOException here makes ExoPlayer rebuffer and retry
     * the load instead of decoding corrupt frames.
     *
     * v5 seek fix: "not here yet" is BUFFERING, not failure. A +10s
     * seek lands on a range the download has not reached, and the
     * old code threw after 3 immediate attempts — so the seek showed
     * the playback-error screen instead of the buffering spinner.
     * This loop now waits patiently (up to 60s) for the range to
     * arrive, proving coverage from the returned state on EVERY
     * attempt. A synchronous DownloadFile that returns early
     * (superseded / failed download) proves nothing and is retried,
     * never trusted. Only a genuinely stuck download — nothing
     * proven after 60s — still throws, so the error screen with
     * Retry remains for real failures.
     *
     * v7: the loop no longer blocks on the whole 8 MB lookahead —
     * it waits ONLY for the exact read range (plus the 1 MB seed
     * when a force-fetch is needed), polling TDLib's cached state
     * every 100 ms. The 8 MB ahead is the background prefetch's
     * job, not this read's. That is the whole latency fix: a seek
     * used to wait for 8 MB here AND 8 MB in open() before a single
     * frame; now it waits for ~1 MB and plays while the rest
     * downloads behind it.
     *
     * How a range is proven downloaded (in order):
     *  1. the whole file is complete, or
     *  2. the contiguous readable prefix (downloadedPrefixSize,
     *     starting at downloadOffset) covers it, or
     *  3. our own bookkeeping: a range already proven in this
     *     session covers it (provenRanges — sound for out-of-order
     *     downloads, unlike the old single high-water mark).
     * A null file state is retried like any other unproven state.
     */
    private fun ensureDownloaded(position: Long, length: Long) {
        val info = fileInfo ?: return
        if (info.isComplete) return
        if (isProven(position, length)) return
        // A background prefetch may already have finished this
        // range — the cached TDLib state is free to read, so check
        // it before issuing any blocking call.
        gateway.getCachedFileState(fileId)?.let { fresh ->
            fileInfo = fresh
            if (covers(fresh, position, length)) {
                addProven(fresh)
                return
            }
        }
        // Make sure a download is in flight for this range.
        runBlocking { maybePrefetch(position) }
        val deadline = SystemClock.elapsedRealtime() + MAX_ENSURE_WAIT_MS
        var lastForceMs = 0L
        while (true) {
            // The player is tearing down — stop waiting at once.
            if (!opened) {
                throw java.io.IOException("Download interrupted (file $fileId)")
            }
            gateway.getCachedFileState(fileId)?.let { fresh ->
                fileInfo = fresh
                if (covers(fresh, position, length)) {
                    addProven(fresh)
                    return
                }
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastForceMs >= FORCE_RETRY_MS) {
                lastForceMs = now
                val downloaded = runBlocking {
                    runCatching {
                        gateway.ensureRange(fileId, position, maxOf(length, INITIAL_BYTES))
                    }.getOrNull()
                }
                if (downloaded != null) {
                    fileInfo = downloaded
                    if (covers(downloaded, position, length)) {
                        addProven(downloaded)
                        return
                    }
                }
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw java.io.IOException(
                    "Could not download video bytes at $position " +
                        "(file $fileId) after ${MAX_ENSURE_WAIT_MS / 1000}s"
                )
            }
            runBlocking { delay(ENSURE_POLL_MS) }
        }
    }

    /**
     * v7: extend the background prefetch window when the read
     * position is getting close to its end. Asks TDLib (non-blocking
     * DownloadFile) for the next 8 MB from the window end — or from
     * the read position when the window is behind it (fresh seek).
     * The download keeps running in TDLib after this returns;
     * progress arrives via UpdateFile and is visible through
     * getCachedFileState(). Never blocks, never throws.
     */
    private suspend fun maybePrefetch(readPos: Long) {
        if (readPos + PREFETCH_TRIGGER_BYTES <= prefetchRequestedUntil) return
        val start = maxOf(prefetchRequestedUntil, readPos)
        runCatching { gateway.startRangeDownload(fileId, start, PREFETCH_CHUNK_BYTES) }
        prefetchRequestedUntil = maxOf(prefetchRequestedUntil, start + PREFETCH_CHUNK_BYTES)
    }

    /**
     * v7: how much ONE blocking fetch waits for at [position] —
     * 1 MB, or less when fewer bytes remain in the file (a fetch
     * past EOF can never prove coverage, so never ask for it).
     */
    private fun seedBytes(sizeBytes: Long, position: Long): Long {
        if (sizeBytes <= 0L) return INITIAL_BYTES
        return minOf(INITIAL_BYTES, (sizeBytes - position).coerceAtLeast(1L))
    }

    /**
     * v7: record the contiguous prefix a file state proves as
     * downloaded. The whole prefix is proven, not just the range
     * that was asked for — so a later read anywhere inside it is
     * instant, including a seek back.
     */
    private fun addProven(info: VideoFileInfo) {
        if (info.isComplete) {
            if (info.sizeBytes > 0L) addProvenRange(0L, info.sizeBytes)
            return
        }
        val end = info.downloadOffset + info.downloadedPrefixSize
        if (end > info.downloadOffset) addProvenRange(info.downloadOffset, end)
    }

    /** Merge [start, end) into the proven set. */
    private fun addProvenRange(start: Long, end: Long) {
        if (end <= start) return
        var s = start
        var e = end
        val it = provenRanges.iterator()
        while (it.hasNext()) {
            val (rs, re) = it.next()
            if (re < s || rs > e) continue
            s = minOf(s, rs)
            e = maxOf(e, re)
            it.remove()
        }
        provenRanges.add(s to e)
    }

    /** True when a proven interval fully covers [position, position+length). */
    private fun isProven(position: Long, length: Long): Boolean {
        val end = position + length
        return provenRanges.any { (s, e) -> s <= position && e >= end }
    }

    /**
     * True when [info] PROVES the bytes [position, position+length)
     * are readable: the whole file is complete, or the contiguous
     * downloaded prefix (which starts at downloadOffset, not
     * always at 0 — a range download reports its own offset) covers
     * the range. downloadedSize is progress-only and is never used
     * here — bytes beyond the prefix may be garbage.
     */
    private fun covers(info: VideoFileInfo, position: Long, length: Long): Boolean {
        if (info.isComplete) return true
        val prefixStart = info.downloadOffset
        val prefixEnd = prefixStart + info.downloadedPrefixSize
        return prefixStart <= position && prefixEnd >= position + length
    }

    override fun close() {
        if (opened) {
            opened = false
            try {
                randomAccessFile?.close()
            } catch (_: Exception) {
            } finally {
                randomAccessFile = null
            }
            currentUri = null
            transferEnded()
        }
    }

    /** Factory used by ExoPlayer's ProgressiveMediaSource. */
    class Factory(private val gateway: TelegramGateway) : DataSource.Factory {
        override fun createDataSource(): DataSource = TdlibDataSource(gateway)
    }
}
