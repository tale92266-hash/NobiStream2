package com.nobi.nobistream2.data

import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.core.VideoQuality
import com.nobi.nobistream2.data.db.AppDatabase
import com.nobi.nobistream2.data.db.DownloadEntity
import com.nobi.nobistream2.data.db.WatchLaterEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Watch Later + Downloads, backed by Room. No fake state: download
 * progress comes from TDLib's own file state via [TelegramGateway].
 */
class LibraryStore(
    private val database: AppDatabase,
    private val gateway: TelegramGateway
) {

    // ---- Watch Later ----

    fun observeWatchLater(): Flow<List<VideoItem>> =
        database.watchLaterDao().observeAll().map { rows -> rows.map { it.toVideo() } }

    suspend fun isWatchLater(item: VideoItem): Boolean =
        database.watchLaterDao().count(item.chatId, item.messageId) > 0

    suspend fun toggleWatchLater(item: VideoItem): Boolean {
        val dao = database.watchLaterDao()
        return if (dao.count(item.chatId, item.messageId) > 0) {
            dao.delete(item.chatId, item.messageId)
            false
        } else {
            dao.upsert(
                WatchLaterEntity(
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
                    savedAtEpochSec = System.currentTimeMillis() / 1000L
                )
            )
            true
        }
    }

    suspend fun removeWatchLater(item: VideoItem) {
        database.watchLaterDao().delete(item.chatId, item.messageId)
    }

    // ---- Downloads ----

    fun observeDownloads(): Flow<List<DownloadEntity>> =
        database.downloadDao().observeAll()

    suspend fun getDownload(item: VideoItem): DownloadEntity? =
        database.downloadDao().get(item.chatId, item.messageId)

    suspend fun removeDownload(item: VideoItem) {
        database.downloadDao().delete(item.chatId, item.messageId)
    }

    suspend fun clearCompletedDownloads() {
        database.downloadDao().deleteCompleted()
    }

    /**
     * Download a whole video file for offline watching, in 8 MB ranges
     * through the gateway (the same range machinery the player uses).
     * [onProgress] receives (downloadedBytes, totalBytes). Returns true
     * only when TDLib reports the file complete AND the local file exists.
     * [isPaused] is checked between ranges so Pause actually pauses.
     * A range download that throws is recorded as FAILED, never as done.
     */
    suspend fun downloadVideo(
        item: VideoItem,
        isPaused: () -> Boolean,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        val now = { System.currentTimeMillis() / 1000L }
        val dao = database.downloadDao()
        val resolvedId = runCatching { gateway.resolveFileId(item.remoteFileId) }.getOrNull()
            ?: item.fileId
        val total = item.sizeBytes.coerceAtLeast(0L)
        dao.upsert(
            DownloadEntity(
                chatId = item.chatId, messageId = item.messageId,
                title = item.title, qualityLabel = item.quality.label,
                durationSec = item.durationSec, sizeBytes = total,
                dateEpochSec = item.dateEpochSec, thumbLocalPath = item.thumbLocalPath,
                fileId = resolvedId, remoteFileId = item.remoteFileId,
                status = "DOWNLOADING", downloadedBytes = 0L, localPath = null,
                updatedAtEpochSec = now()
            )
        )
        return try {
            var offset = 0L
            val chunk = 8L * 1024L * 1024L
            var info = gateway.downloadFileInfo(resolvedId)
            var localPath: String? = info?.localPath?.takeIf { it.isNotBlank() }
            while (total <= 0L || offset < total) {
                if (isPaused()) {
                    dao.upsert(
                        DownloadEntity(
                            chatId = item.chatId, messageId = item.messageId,
                            title = item.title, qualityLabel = item.quality.label,
                            durationSec = item.durationSec, sizeBytes = total,
                            dateEpochSec = item.dateEpochSec, thumbLocalPath = item.thumbLocalPath,
                            fileId = resolvedId, remoteFileId = item.remoteFileId,
                            status = "PAUSED", downloadedBytes = offset, localPath = localPath,
                            updatedAtEpochSec = now()
                        )
                    )
                    return false
                }
                val len = if (total > 0L) minOf(chunk, total - offset) else chunk
                info = gateway.ensureRange(resolvedId, offset, len) ?: break
                localPath = info.localPath.takeIf { it.isNotBlank() } ?: localPath
                val done = info.downloadedBytes.coerceAtLeast(offset + len)
                offset = if (total > 0L) minOf(done, total) else done
                onProgress(offset, total)
                if (total <= 0L) break
                // ensureRange proves the range; loop until the whole file is proven.
                if (info.isComplete) break
            }
            val state = gateway.getCachedFileState(resolvedId)
            val complete = (state?.isComplete == true || info?.isComplete == true) &&
                !localPath.isNullOrBlank()
            dao.upsert(
                DownloadEntity(
                    chatId = item.chatId, messageId = item.messageId,
                    title = item.title, qualityLabel = item.quality.label,
                    durationSec = item.durationSec, sizeBytes = total,
                    dateEpochSec = item.dateEpochSec, thumbLocalPath = item.thumbLocalPath,
                    fileId = resolvedId, remoteFileId = item.remoteFileId,
                    status = if (complete) "COMPLETED" else "FAILED",
                    downloadedBytes = offset, localPath = localPath,
                    updatedAtEpochSec = now()
                )
            )
            complete
        } catch (_: Exception) {
            dao.upsert(
                DownloadEntity(
                    chatId = item.chatId, messageId = item.messageId,
                    title = item.title, qualityLabel = item.quality.label,
                    durationSec = item.durationSec, sizeBytes = total,
                    dateEpochSec = item.dateEpochSec, thumbLocalPath = item.thumbLocalPath,
                    fileId = resolvedId, remoteFileId = item.remoteFileId,
                    status = "FAILED", downloadedBytes = 0L, localPath = null,
                    updatedAtEpochSec = now()
                )
            )
            false
        }
    }

    private fun WatchLaterEntity.toVideo(): VideoItem = VideoItem(
        messageId = messageId, chatId = chatId, fileId = fileId,
        remoteFileId = remoteFileId, title = title, caption = caption,
        quality = VideoQuality.fromLabel(qualityLabel), durationSec = durationSec,
        sizeBytes = sizeBytes, dateEpochSec = dateEpochSec,
        thumbFileId = thumbFileId, thumbLocalPath = thumbLocalPath
    )
}
