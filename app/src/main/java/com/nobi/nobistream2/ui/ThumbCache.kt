package com.nobi.nobistream2.ui

import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.data.NobiRepository
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory thumbnail cache + the v4-fb2 fallback chain from the old app:
 * (1) a thumbnail path already saved in Room, (2) the stored thumbnail id
 * in this session, (3) re-read the message for a FRESH thumbnail id and
 * download that. Local TDLib file ids are ephemeral — one client lifetime —
 * so a stored id from before a restart is dead; the re-resolve is the only
 * way to get a usable one. A successfully downloaded path is written back
 * to Room so the next bind loads from disk. Null = genuinely no thumbnail.
 */
object ThumbCache {
    private val cache = ConcurrentHashMap<Pair<Long, Long>, String>()

    suspend fun resolve(repo: NobiRepository, item: VideoItem): String? {
        val key = item.chatId to item.messageId
        cache[key]?.let { if (File(it).exists()) return it }
        val local = item.thumbLocalPath
        if (!local.isNullOrBlank() && File(local).exists()) {
            cache[key] = local
            return local
        }
        var path: String? = null
        if (item.thumbFileId != null) {
            path = runCatching { repo.downloadThumbnail(item.thumbFileId) }.getOrNull()
        }
        if (path.isNullOrBlank()) {
            path = runCatching { repo.refreshThumbnail(item) }.getOrNull()
        }
        if (!path.isNullOrBlank() && File(path).exists()) {
            cache[key] = path
            runCatching { repo.saveThumbPath(item, path) }
            return path
        }
        return null
    }
}
