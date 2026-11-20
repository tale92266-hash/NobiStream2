package com.nobi.nobistream2.data

import android.content.Context
import com.nobi.nobistream2.core.AuthState
import com.nobi.nobistream2.core.GroupItem
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.core.VideoQuality
import com.nobi.nobistream2.data.db.AppDatabase
import com.nobi.nobistream2.data.db.SavedGroupEntity
import com.nobi.nobistream2.data.db.VideoPositionEntity
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The only class screens talk to for Telegram data. It combines the TDLib
 * gateway with the local Room database (saved groups, resume positions).
 */
class NobiRepository(
    private val context: Context,
    private val gateway: TelegramGateway,
    private val database: AppDatabase,
    val settingsStore: SettingsStore,
    val credentialsStore: ApiCredentialsStore
) {

    val authState: Flow<AuthState> = gateway.authState

    val currentUser = gateway.currentUser

    /** Start the Telegram client if credentials exist. False when none exist. */
    suspend fun ensureStarted(): Boolean {
        if (!credentialsStore.hasCredentials()) return false
        if (gateway.isStarted) return true
        return try {
            gateway.start(credentialsStore.getApiId(), credentialsStore.getApiHash())
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun sendPhoneNumber(phone: String) = gateway.sendPhoneNumber(phone)

    suspend fun sendCode(code: String) = gateway.sendCode(code)

    suspend fun sendPassword(password: String) = gateway.sendPassword(password)

    suspend fun logout() {
        gateway.logout()
    }

    fun observeGroups(): Flow<List<GroupItem>> =
        database.groupDao().observeGroups().map { rows ->
            rows.map { row ->
                GroupItem(
                    chatId = row.chatId,
                    username = row.username,
                    title = row.title,
                    photoLocalPath = row.photoLocalPath,
                    videoCount = row.videoCount,
                    lastUpdatedEpochSec = row.lastUpdatedEpochSec
                )
            }
        }

    suspend fun addGroup(link: String): GroupItem {
        val group = gateway.resolveChat(link)
        database.groupDao().upsert(
            SavedGroupEntity(
                chatId = group.chatId,
                username = group.username,
                title = group.title,
                photoLocalPath = group.photoLocalPath,
                videoCount = group.videoCount,
                lastUpdatedEpochSec = group.lastUpdatedEpochSec
            )
        )
        return group
    }

    suspend fun removeGroup(chatId: Long) {
        database.groupDao().delete(chatId)
    }

    /** Load one page of videos and merge in saved resume positions. */
    suspend fun loadVideos(chatId: Long, fromMessageId: Long, limit: Int = 50): VideoPage {
        val page = gateway.loadVideos(chatId, fromMessageId, limit)
        if (fromMessageId == 0L && page.totalCount > 0) {
            database.groupDao().updateCount(
                chatId,
                page.totalCount,
                System.currentTimeMillis() / 1000L
            )
        }
        if (page.videos.isEmpty()) return page
        val positions = database.positionDao().getPositionsForChat(chatId)
            .associateBy { it.messageId }
        val merged = page.videos.map { video ->
            val saved = positions[video.messageId]
            if (saved != null) {
                video.copy(progressSec = saved.positionSec, watched = saved.watched)
            } else {
                video
            }
        }
        // v4-fb3: preserve the pagination fields — the old code rebuilt
        // the page with only videos + totalCount, so lastMessageId was
        // always 0 and rawCount always 0 downstream: ChannelActivity
        // then set hasMore = (0 >= 50) = false after the FIRST page,
        // hiding Load more and stopping scroll-load. That is why lists
        // stopped after one page even though more videos existed.
        return VideoPage(
            videos = merged,
            totalCount = page.totalCount,
            lastMessageId = page.lastMessageId,
            rawCount = page.rawCount
        )
    }

    /**
     * v4-fb3: resolve a FRESH local file id for a Room video item in
     * THIS TDLib session. Local file ids are ephemeral (one client
     * lifetime — proven by the fb2 thumbnail failure, same mechanism);
     * an item saved before a restart carries a dead fileId, and the
     * data source's DownloadFile(staleId) throws → "Video couldn't
     * play" on every item, including Retry and Next/Previous. The
     * remote id is persistent, so GetRemoteFile re-resolves it; if
     * that fails, re-read the message itself. Returns the item
     * unchanged only when both fail, so the player fails honestly
     * instead of silently playing a dead id.
     *
     * v6: [resolveVideoItemDetailed] also reports whether EITHER
     * path succeeded — the player needs to know a genuinely
     * unresolvable file (both failed) so its error screen can say
     * "Couldn't play this video" instead of retrying a stall
     * forever on a file that does not exist.
     */
    data class ResolvedVideo(val item: VideoItem, val freshIdResolved: Boolean)

    suspend fun resolveVideoItemDetailed(item: VideoItem): ResolvedVideo {
        if (item.remoteFileId.isNotEmpty()) {
            val freshId = runCatching { gateway.resolveFileId(item.remoteFileId) }.getOrNull()
            if (freshId != null) {
                return ResolvedVideo(
                    if (freshId != item.fileId) item.copy(fileId = freshId) else item,
                    true
                )
            }
        }
        val fresh = runCatching {
            gateway.getMessageVideo(item.chatId, item.messageId)
        }.getOrNull()
        if (fresh != null) {
            return ResolvedVideo(
                fresh.copy(progressSec = item.progressSec, watched = item.watched),
                true
            )
        }
        return ResolvedVideo(item, false)
    }

    suspend fun resolveVideoItem(item: VideoItem): VideoItem =
        resolveVideoItemDetailed(item).item

    /**
     * v6: subtitle sidecars (.srt/.vtt) attached near this video's
     * message. Never throws — no sidecar found (or discovery
     * failed) is an empty list, and the player then simply shows no
     * CC button. Discovery runs off the playback path, so a slow
     * chat history can never delay the video starting.
     */
    suspend fun findSubtitles(item: VideoItem): List<SubtitleTrack> =
        runCatching { gateway.findSubtitleFiles(item.chatId, item.messageId) }
            .getOrDefault(emptyList())

    suspend fun downloadThumbnail(fileId: Int): String? =
        gateway.downloadThumbnail(fileId)

    /**
     * v4-fb2: re-resolve a video's thumbnail for THIS TDLib session
     * and download it; returns the local path, or null when the
     * message has no thumbnail (e.g. a Document-type video Telegram
     * never thumbnailed) or the download fails. That null is the
     * honest "no thumbnail exists" case — the UI then keeps the
     * graceful placeholder instead of pretending.
     */
    suspend fun refreshThumbnail(item: VideoItem): String? {
        val freshThumbId = gateway.getMessageThumbnailFileId(item.chatId, item.messageId)
            ?: return null
        return downloadThumbnail(freshThumbId)
    }

    /** v4-fb2: persist a downloaded thumbnail path (see PositionDao.updateThumbPath). */
    suspend fun saveThumbPath(item: VideoItem, path: String) {
        database.positionDao().updateThumbPath(item.chatId, item.messageId, path)
    }

    suspend fun savePosition(item: VideoItem, positionSec: Long) {
        val watched = item.durationSec > 0 && positionSec >= item.durationSec - 10L
        database.positionDao().upsert(
            VideoPositionEntity(
                chatId = item.chatId,
                messageId = item.messageId,
                title = item.title,
                caption = item.caption,
                qualityLabel = item.quality.label,
                durationSec = item.durationSec,
                sizeBytes = item.sizeBytes,
                dateEpochSec = item.dateEpochSec,
                thumbFileId = item.thumbFileId,
                thumbLocalPath = item.thumbLocalPath,
                fileId = item.fileId,
                remoteFileId = item.remoteFileId,
                positionSec = positionSec,
                watched = watched,
                updatedAtEpochSec = System.currentTimeMillis() / 1000L
            )
        )
    }

    suspend fun getPositionSec(chatId: Long, messageId: Long): Long =
        database.positionDao().getPosition(chatId, messageId)?.positionSec ?: 0L

    /** The most recently watched unfinished video, or null when there is none. */
    suspend fun getLastWatched(): VideoItem? {
        val row = database.positionDao().getMostRecent() ?: return null
        return VideoItem(
            messageId = row.messageId,
            chatId = row.chatId,
            fileId = row.fileId,
            remoteFileId = row.remoteFileId,
            title = row.title,
            caption = row.caption,
            quality = VideoQuality.fromLabel(row.qualityLabel),
            durationSec = row.durationSec,
            sizeBytes = row.sizeBytes,
            dateEpochSec = row.dateEpochSec,
            thumbFileId = row.thumbFileId,
            thumbLocalPath = row.thumbLocalPath,
            progressSec = row.positionSec,
            watched = row.watched
        )
    }

    fun observeContinueWatching(): Flow<List<VideoItem>> =
        database.positionDao().observeContinueWatching().map { rows ->
            rows.map { rowToVideo(it) }
        }

    /** v4: the full Continue Watching list (no 10-item cap), newest first. */
    fun observeContinueWatchingAll(): Flow<List<VideoItem>> =
        database.positionDao().observeContinueWatchingAll().map { rows ->
            rows.map { rowToVideo(it) }
        }

    private fun rowToVideo(row: VideoPositionEntity): VideoItem =
        VideoItem(
            messageId = row.messageId,
            chatId = row.chatId,
            fileId = row.fileId,
            remoteFileId = row.remoteFileId,
            title = row.title,
            caption = row.caption,
            quality = VideoQuality.fromLabel(row.qualityLabel),
            durationSec = row.durationSec,
            sizeBytes = row.sizeBytes,
            dateEpochSec = row.dateEpochSec,
            thumbFileId = row.thumbFileId,
            thumbLocalPath = row.thumbLocalPath,
            progressSec = row.positionSec,
            watched = row.watched
        )

    /**
     * v4: search video titles/captions across ALL added groups.
     * Primary route is TDLib server-side search per group
     * (SearchChatMessages + video filter). When that throws for a
     * group (e.g. search unsupported there), it falls back to
     * filtering that group's most recent loaded page locally, so a
     * search still returns something instead of failing outright.
     * Results are merged, de-duplicated and newest first, with saved
     * resume positions merged in like loadVideos does.
     */
    suspend fun searchVideos(query: String, groups: List<GroupItem>): List<VideoItem> {
        val clean = query.trim()
        if (clean.isEmpty() || groups.isEmpty()) return emptyList()
        val found = mutableListOf<VideoItem>()
        for (group in groups) {
            val page = try {
                gateway.searchVideos(group.chatId, clean, 50)
            } catch (_: Exception) {
                runCatching { loadVideos(group.chatId, 0L, 50) }.getOrNull()
                    ?.let { loaded ->
                        VideoPage(
                            videos = loaded.videos.filter {
                                it.title.contains(clean, ignoreCase = true) ||
                                    it.caption.contains(clean, ignoreCase = true)
                            },
                            totalCount = loaded.totalCount
                        )
                    } ?: continue
            }
            found.addAll(page.videos)
        }
        // Merge saved resume positions (server search returns none).
        val merged = found.map { video ->
            val saved = database.positionDao()
                .getPosition(video.chatId, video.messageId)
            if (saved != null) {
                video.copy(progressSec = saved.positionSec, watched = saved.watched)
            } else {
                video
            }
        }
        val seen = HashSet<Pair<Long, Long>>()
        return merged
            .filter { seen.add(it.chatId to it.messageId) }
            .sortedByDescending { it.dateEpochSec }
    }

    /** One merged page of the Home library: first/next page per group. */
    data class LibraryPage(
        val videos: List<VideoItem>,
        /** Next fromMessageId per group (0 = group not started). */
        val cursors: Map<Long, Long>,
        /** True when at least one group returned a full page. */
        val hasMore: Boolean
    )

    /**
     * v4: load one library page — one history page from EACH added
     * group, merged newest first. [cursors] maps chatId to the
     * fromMessageId to continue from (0 for the first page). A group
     * that fails to load is skipped, not fatal to the whole page.
     */
    suspend fun loadLibraryPage(
        groups: List<GroupItem>,
        cursors: Map<Long, Long>,
        limit: Int = 50
    ): LibraryPage {
        val all = mutableListOf<VideoItem>()
        val nextCursors = cursors.toMutableMap()
        var hasMore = false
        for (group in groups) {
            val from = cursors[group.chatId] ?: 0L
            val page = try {
                loadVideos(group.chatId, from, limit)
            } catch (_: Exception) {
                continue
            }
            if (page.videos.isNotEmpty()) {
                all.addAll(page.videos)
            }
            // v4: advance on the oldest RAW message (see VideoPage):
            // a window can hold few videos, and the old video-count
            // hasMore stopped the library after the first thin window.
            if (page.lastMessageId > 0L) {
                nextCursors[group.chatId] = page.lastMessageId
            }
            if (page.rawCount >= limit) hasMore = true
        }
        all.sortByDescending { it.dateEpochSec }
        return LibraryPage(videos = all, cursors = nextCursors, hasMore = hasMore)
    }

    /** Outcome of a Home groups refresh: how many re-fetched OK. */
    data class RefreshResult(val refreshed: Int, val failed: Int)

    /**
     * v4: re-fetch each saved group's info from Telegram — fresh
     * title and photo via resolveChat, fresh video count via a
     * 1-item history load (totalCount is what TDLib actually
     * returns; nothing is invented). A group that fails is counted
     * and skipped; the rest still refresh.
     */
    suspend fun refreshGroups(groups: List<GroupItem>): RefreshResult {
        var ok = 0
        var failed = 0
        val nowSec = System.currentTimeMillis() / 1000L
        for (group in groups) {
            try {
                val fresh = gateway.resolveChat(group.username)
                val page = loadVideos(group.chatId, 0L, 1)
                database.groupDao().upsert(
                    SavedGroupEntity(
                        chatId = group.chatId,
                        username = group.username,
                        title = fresh.title,
                        photoLocalPath = fresh.photoLocalPath ?: group.photoLocalPath,
                        videoCount = if (page.totalCount > 0) page.totalCount else group.videoCount,
                        lastUpdatedEpochSec = nowSec
                    )
                )
                ok++
            } catch (_: Exception) {
                failed++
            }
        }
        return RefreshResult(refreshed = ok, failed = failed)
    }

    /** Delete ExoPlayer / image caches. TDLib's own files are left alone. */
    suspend fun clearCache() {
        val cacheDir = context.cacheDir
        File(cacheDir, "media3").deleteRecursively()
        File(cacheDir, "exoplayer").deleteRecursively()
        File(cacheDir, "image_cache").deleteRecursively()
    }
}
