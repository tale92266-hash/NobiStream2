package com.nobi.nobistream2.data

import com.nobi.nobistream2.core.AuthState
import com.nobi.nobistream2.core.GroupItem
import com.nobi.nobistream2.core.TelegramUser
import com.nobi.nobistream2.core.VideoItem
import kotlinx.coroutines.flow.StateFlow

/** Local info about one TDLib file, enough for the player to stream it. */
data class VideoFileInfo(
    val fileId: Int,
    val sizeBytes: Long,
    val localPath: String,
    val downloadedBytes: Long,
    val isComplete: Boolean,
    /**
     * v4: the contiguous READABLE prefix starting at [downloadOffset]
     * (TL localFile.downloaded_prefix_size). THIS is the readiness
     * field. [downloadedBytes] (downloaded_size) is progress-only —
     * the TL scheme says parts beyond the prefix "may contain
     * garbage" — so it must never gate reads, which is exactly what
     * the old code did.
     */
    val downloadOffset: Long = 0L,
    val downloadedPrefixSize: Long = 0L
)

/** One subtitle sidecar file (.srt/.vtt) found near a video message. */
data class SubtitleTrack(
    val fileId: Int,
    val fileName: String,
    /** ISO language code parsed from the file name, or null when unknown. */
    val language: String?,
    val isVtt: Boolean,
    /** Local path of the downloaded subtitle file. */
    val localPath: String
)

/** One page of chat history. */
data class VideoPage(
    val videos: List<VideoItem>,
    val totalCount: Int,
    /**
     * v4: id of the OLDEST raw message TDLib returned in this page
     * (0 when the page was empty). Pagination must advance on this,
     * not on the last video: a 50-message window can hold few videos
     * (text posts in between), and advancing only on videos stalls
     * paging — the old code then also set hasMore from the video
     * count, so the list just stopped.
     */
    val lastMessageId: Long = 0L,
    /** Raw messages TDLib returned (videos + text + everything). */
    val rawCount: Int = 0
)

/**
 * Seam between the app and Telegram (MTProto via TDLib). The rest of the app
 * only talks to this interface, so the TDLib implementation can be replaced
 * without touching any screen.
 */
interface TelegramGateway {

    /** Current login state, driven by TDLib authorization updates. */
    val authState: StateFlow<AuthState>

    /** Signed-in user once [AuthState.READY], null otherwise. */
    val currentUser: StateFlow<TelegramUser?>

    /** True once a TDLib client has been created in this process. */
    val isStarted: Boolean

    /** Create the TDLib client and start the login handshake. Idempotent. */
    suspend fun start(apiId: Int, apiHash: String)

    /** Send the phone number (with country code, e.g. +91...). */
    suspend fun sendPhoneNumber(phone: String)

    /** Send the verification code Telegram sent to the account. */
    suspend fun sendCode(code: String)

    /** Send the two-step verification password, when TDLib asks for it. */
    suspend fun sendPassword(password: String)

    /** Log out of Telegram. Saved groups stay; sign-in is needed again. */
    suspend fun logout()

    /** Resolve a t.me link / @username / username to a group or channel. */
    suspend fun resolveChat(linkOrUsername: String): GroupItem

    /** Load one page of video messages, newest first. */
    suspend fun loadVideos(chatId: Long, fromMessageId: Long, limit: Int): VideoPage

    /**
     * Server-side search of video messages in one chat (TDLib
     * SearchChatMessages + SearchMessagesFilterVideo). Returns newest
     * first; empty when nothing matches.
     */
    suspend fun searchVideos(chatId: Long, query: String, limit: Int): VideoPage

    /** Download a thumbnail file synchronously; returns its local path. */
    suspend fun downloadThumbnail(fileId: Int): String?

    /**
     * v4-fb2: fresh thumbnail file id for one message in THIS TDLib
     * session (via GetMessage), or null when the message has no
     * thumbnail. Local file ids are ephemeral — one client lifetime —
     * so an id saved in Room before a restart is dead; re-reading
     * the message is the only way to get a usable one.
     */
    suspend fun getMessageThumbnailFileId(chatId: Long, messageId: Long): Int?

    /**
     * v4-fb3: resolve a fresh LOCAL file id for a persistent remote
     * file id in THIS TDLib session (GetRemoteFile, offline). Local
     * file ids are ephemeral — one client lifetime — so an id saved
     * in Room before a restart is dead; the remote id is the only
     * stable handle. Null when the remote reference is gone.
     */
    suspend fun resolveFileId(remoteFileId: String): Int?

    /**
     * v4-fb3: re-read one message in THIS TDLib session and map it
     * to a fresh VideoItem (fresh local file id + thumbnail id), or
     * null when the message is gone / not a video. Fallback when
     * GetRemoteFile cannot resolve the stored remote id.
     */
    suspend fun getMessageVideo(chatId: Long, messageId: Long): VideoItem?

    /** Info about a video file, starting its download if needed. */
    suspend fun downloadFileInfo(fileId: Int): VideoFileInfo?

    /** Synchronously download the requested byte range of a video file. */
    suspend fun ensureRange(fileId: Int, offset: Long, length: Long): VideoFileInfo?

    /**
     * v7: the most recent file state TDLib reported for this file
     * (UpdateFile), WITHOUT starting a download or blocking. Null
     * when TDLib has reported nothing yet in this session. The data
     * source polls this (free) while a range download runs, so a
     * wait loop never issues a blocking DownloadFile per check.
     */
    fun getCachedFileState(fileId: Int): VideoFileInfo?

    /**
     * v7: start downloading [length] bytes from [offset] in the
     * BACKGROUND (DownloadFile, synchronous = false) and return at
     * once with the file's current state. The download keeps running
     * in TDLib; progress arrives via UpdateFile and is visible in
     * [getCachedFileState]. Used for prefetch: the player starts
     * from a small proven chunk while the bytes ahead download.
     */
    suspend fun startRangeDownload(fileId: Int, offset: Long, length: Long): VideoFileInfo?

    /**
     * v6 subtitles: subtitle sidecar files (.srt/.vtt) attached as
     * documents near the video's message in the same chat. TDLib's
     * MessageVideo has NO subtitle field, so this searches the
     * messages around the video message instead. Empty when the
     * video has no sidecar — the player then shows no CC button,
     * never invented subtitles. Each returned track is already
     * downloaded (small files); [SubtitleTrack.localPath] is its
     * local path. Never throws for "nothing found".
     */
    suspend fun findSubtitleFiles(chatId: Long, messageId: Long): List<SubtitleTrack>

    /** Download a small file (e.g. a subtitle sidecar); its local path, or null. */
    suspend fun downloadSubtitle(fileId: Int): String?

    /** Release the TDLib client. */
    fun close()
}
