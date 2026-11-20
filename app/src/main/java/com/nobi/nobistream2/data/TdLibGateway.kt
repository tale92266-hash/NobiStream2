package com.nobi.nobistream2.data

import android.content.Context
import android.os.Build
import com.nobi.nobistream2.core.AuthState
import com.nobi.nobistream2.core.GroupItem
import com.nobi.nobistream2.core.QualityParser
import com.nobi.nobistream2.core.TelegramUser
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.core.VideoQuality
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resumeWithException

/**
 * [TelegramGateway] on top of TDLib's official Java bindings
 * (org.drinkless.tdlib.Client / TdApi, TDLib 1.8.64, prebuilt AAR).
 *
 * The login flow is driven by TDLib authorization-state updates, exactly as
 * the official TDLib example does: WaitTdlibParameters -> send parameters,
 * WaitPhoneNumber -> UI asks for the phone number, WaitCode -> UI asks for
 * the code, WaitPassword -> UI asks for the 2FA password, Ready -> signed in.
 */
class TdLibGateway(context: Context) : TelegramGateway {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val authStateFlow = MutableStateFlow(AuthState.UNKNOWN)
    override val authState: StateFlow<AuthState> = authStateFlow.asStateFlow()

    private val userFlow = MutableStateFlow<TelegramUser?>(null)
    override val currentUser: StateFlow<TelegramUser?> = userFlow.asStateFlow()

    private val fileStates = ConcurrentHashMap<Int, TdApi.File>()
    private val paramsSent = AtomicBoolean(false)

    @Volatile
    private var client: Client? = null

    @Volatile
    private var pendingApiId: Int = 0

    @Volatile
    private var pendingApiHash: String = ""

    override val isStarted: Boolean
        get() = client != null

    private val updateHandler = Client.ResultHandler { obj -> onUpdate(obj) }

    private fun onUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> onAuthState(obj.authorizationState)
            is TdApi.UpdateFile -> fileStates[obj.file.id] = obj.file
        }
    }

    private fun onAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                if (paramsSent.compareAndSet(false, true)) {
                    sendParams()
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber ->
                authStateFlow.value = AuthState.NEED_PHONE
            is TdApi.AuthorizationStateWaitCode ->
                authStateFlow.value = AuthState.NEED_CODE
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation ->
                authStateFlow.value = AuthState.NEED_CODE
            is TdApi.AuthorizationStateWaitEmailAddress,
            is TdApi.AuthorizationStateWaitEmailCode ->
                authStateFlow.value = AuthState.NEED_CODE
            is TdApi.AuthorizationStateWaitPassword ->
                authStateFlow.value = AuthState.NEED_PASSWORD
            is TdApi.AuthorizationStateWaitRegistration ->
                authStateFlow.value = AuthState.NEED_PHONE
            is TdApi.AuthorizationStateReady -> {
                authStateFlow.value = AuthState.READY
                scope.launch { refreshMe() }
            }
            is TdApi.AuthorizationStateLoggingOut ->
                authStateFlow.value = AuthState.LOGGED_OUT
            is TdApi.AuthorizationStateClosed -> {
                authStateFlow.value = AuthState.LOGGED_OUT
                userFlow.value = null
            }
            else -> Unit
        }
    }

    private fun sendParams() {
        val baseDir = File(appContext.filesDir, "tdlib")
        val dbDir = File(baseDir, "db").apply { mkdirs() }
        val filesDir = File(baseDir, "files").apply { mkdirs() }
        val params = TdApi.SetTdlibParameters(
            false,
            dbDir.absolutePath,
            filesDir.absolutePath,
            ByteArray(0),
            true,
            true,
            true,
            false,
            pendingApiId,
            pendingApiHash,
            "en",
            "Android",
            Build.VERSION.RELEASE ?: "13",
            "1.0.1"
        )
        client?.send(
            params,
            Client.ResultHandler { },
            Client.ExceptionHandler { paramsSent.set(false) }
        )
    }

    override suspend fun start(apiId: Int, apiHash: String) {
        if (client != null) return
        pendingApiId = apiId
        pendingApiHash = apiHash
        paramsSent.set(false)
        withContext(Dispatchers.IO) {
            try {
                System.loadLibrary("tdjni")
            } catch (_: Throwable) {
                // Client loads the library itself when present; ignore.
            }
            try {
                Client.execute(TdApi.SetLogVerbosityLevel(1))
            } catch (_: Throwable) {
                // Logging is best-effort.
            }
            val created = Client.create(
                updateHandler,
                Client.ExceptionHandler { },
                Client.ExceptionHandler { }
            )
            client = created
        }
        // Wait until the handshake reaches a login step or Ready. If the
        // parameters were rejected (bad credentials), TDLib stays in
        // WaitTdlibParameters and this times out.
        val reached = withTimeoutOrNull(30_000L) {
            authStateFlow.first { it != AuthState.UNKNOWN }
        }
        if (reached == null) {
            throw IllegalStateException(
                "Could not connect to Telegram. Check the API credentials and try again."
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : TdApi.Object> send(fn: TdApi.Function<T>): T =
        suspendCancellableCoroutine { cont ->
            val activeClient = client
            if (activeClient == null) {
                cont.resumeWithException(IllegalStateException("Telegram client is not started"))
                return@suspendCancellableCoroutine
            }
            activeClient.send(
                fn,
                Client.ResultHandler { result ->
                    if (!cont.isActive) return@ResultHandler
                    if (result is TdApi.Error) {
                        cont.resumeWithException(
                            IllegalStateException("Telegram error ${result.code}: ${result.message}")
                        )
                    } else {
                        try {
                            cont.resume(result as T, null)
                        } catch (e: ClassCastException) {
                            cont.resumeWithException(e)
                        }
                    }
                },
                Client.ExceptionHandler { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
            )
        }

    private suspend fun refreshMe() {
        try {
            val me = send(TdApi.GetMe())
            val name = listOf(me.firstName, me.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { me.firstName }
            // TDLib 1.8: usernames live in User.usernames — activeUsernames
            // (array; first is primary), falling back to editableUsername.
            val username = me.usernames?.let { u ->
                u.activeUsernames.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: u.editableUsername.takeIf { it.isNotBlank() }
            }
            val phone = me.phoneNumber.takeIf { it.isNotBlank() }
            userFlow.value = TelegramUser(name, username, phone)
        } catch (_: Exception) {
            // Not fatal; the name is only shown in the UI.
        }
    }

    override suspend fun sendPhoneNumber(phone: String) {
        val clean = phone.trim().replace(" ", "").replace("-", "")
        val full = if (clean.startsWith("+")) clean else "+$clean"
        send(
            TdApi.SetAuthenticationPhoneNumber(
                full,
                TdApi.PhoneNumberAuthenticationSettings(
                    false, false, false, false, false, null, null
                )
            )
        )
    }

    override suspend fun sendCode(code: String) {
        send(TdApi.CheckAuthenticationCode(code.trim()))
    }

    override suspend fun sendPassword(password: String) {
        send(TdApi.CheckAuthenticationPassword(password))
    }

    override suspend fun logout() {
        try {
            send(TdApi.LogOut())
        } finally {
            userFlow.value = null
        }
    }

    override suspend fun resolveChat(linkOrUsername: String): GroupItem {
        val username = parseUsername(linkOrUsername)
        val chat = send(TdApi.SearchPublicChat(username))
        var photoPath: String? = null
        val smallPhotoId = chat.photo?.small?.id
        if (smallPhotoId != null) {
            try {
                photoPath = downloadThumbnail(smallPhotoId)
            } catch (_: Exception) {
                photoPath = null
            }
        }
        return GroupItem(
            chatId = chat.id,
            username = username,
            title = chat.title,
            photoLocalPath = photoPath,
            videoCount = 0,
            lastUpdatedEpochSec = System.currentTimeMillis() / 1000L
        )
    }

    override suspend fun loadVideos(chatId: Long, fromMessageId: Long, limit: Int): VideoPage {
        val safeLimit = limit.coerceIn(1, 100)
        val result = send(
            TdApi.GetChatHistory(chatId, fromMessageId, 0, safeLimit, false)
        )
        // With offset 0 the fromMessageId message itself is included again.
        val messages = result.messages.filter { it.id != fromMessageId }
        val videos = messages.mapNotNull { mapMessage(it) }
        return VideoPage(
            videos = videos,
            totalCount = result.totalCount,
            lastMessageId = messages.lastOrNull()?.id ?: 0L,
            rawCount = messages.size
        )
    }

    /**
     * v4 server-side search (TDLib 1.8.64): SearchChatMessages with a
     * SearchMessagesFilterVideo filter, newest first. Uses the message
     * database (enabled in SetTdlibParameters), so it searches the
     * chat's full history on Telegram's side — not just what this
     * phone has loaded. Callers fall back to local filtering when
     * this throws for a chat.
     */
    override suspend fun searchVideos(chatId: Long, query: String, limit: Int): VideoPage {
        val safeLimit = limit.coerceIn(1, 100)
        val found = send(
            TdApi.SearchChatMessages(
                chatId,
                null,
                query,
                null,
                0L,
                0,
                safeLimit,
                TdApi.SearchMessagesFilterVideo()
            )
        )
        // FoundChatMessages is flat: totalCount + messages[] directly
        // (no TdApi.Messages wrapper), plus nextFromMessageId.
        val videos = found.messages.mapNotNull { mapMessage(it) }
        val raw = found.messages
        return VideoPage(
            videos = videos,
            totalCount = found.totalCount,
            lastMessageId = raw.lastOrNull()?.id ?: 0L,
            rawCount = raw.size
        )
    }

    private fun mapMessage(message: TdApi.Message): VideoItem? {
        return when (val content = message.content) {
            is TdApi.MessageVideo -> {
                val video = content.video
                val file = video.video
                val caption = content.caption?.text ?: ""
                // v2: quality comes from the file's real resolution first.
                // The caption text is only a fallback when TDLib reports
                // no dimensions (width/height 0).
                val resolutionQuality =
                    QualityParser.fromResolution(video.width, video.height)
                val quality = if (resolutionQuality != VideoQuality.UNKNOWN) {
                    resolutionQuality
                } else {
                    QualityParser.parseQuality(caption)
                }
                VideoItem(
                    messageId = message.id,
                    chatId = message.chatId,
                    fileId = file.id,
                    remoteFileId = file.remote.id,
                    title = QualityParser.extractTitle(caption, message.id),
                    caption = caption,
                    quality = quality,
                    durationSec = video.duration,
                    sizeBytes = if (file.size > 0L) file.size else file.expectedSize,
                    dateEpochSec = message.date.toLong(),
                    thumbFileId = video.thumbnail?.file?.id,
                    thumbLocalPath = null
                )
            }
            is TdApi.MessageDocument -> {
                val document = content.document
                if (!document.mimeType.startsWith("video")) return null
                val file = document.document
                val caption = content.caption?.text ?: ""
                // TdApi.Document carries no width/height, so video files
                // sent as documents keep the caption-text fallback.
                VideoItem(
                    messageId = message.id,
                    chatId = message.chatId,
                    fileId = file.id,
                    remoteFileId = file.remote.id,
                    title = QualityParser.extractTitle(caption, message.id),
                    caption = caption,
                    quality = QualityParser.parseQuality(caption),
                    durationSec = 0,
                    sizeBytes = if (file.size > 0L) file.size else file.expectedSize,
                    dateEpochSec = message.date.toLong(),
                    thumbFileId = document.thumbnail?.file?.id,
                    thumbLocalPath = null
                )
            }
            else -> null
        }
    }

    override suspend fun downloadThumbnail(fileId: Int): String? {
        val file = send(TdApi.DownloadFile(fileId, 1, 0, 0, true))
        fileStates[file.id] = file
        val path = file.local.path
        return if (path.isNotEmpty() && File(path).exists()) path else null
    }

    override suspend fun getMessageThumbnailFileId(chatId: Long, messageId: Long): Int? {
        val message = send(TdApi.GetMessage(chatId, messageId))
        return mapMessage(message)?.thumbFileId
    }

    override suspend fun resolveFileId(remoteFileId: String): Int? {
        val file = send(TdApi.GetRemoteFile(remoteFileId, null))
        return file.id.takeIf { it != 0 }
    }

    override suspend fun getMessageVideo(chatId: Long, messageId: Long): VideoItem? {
        val message = send(TdApi.GetMessage(chatId, messageId))
        return mapMessage(message)
    }

    override suspend fun downloadFileInfo(fileId: Int): VideoFileInfo? {
        fileStates[fileId]?.let { cached ->
            toInfo(cached)?.let { return it }
        }
        val file = send(TdApi.DownloadFile(fileId, 1, 0, 0, false))
        fileStates[file.id] = file
        return toInfo(file)
    }

    override suspend fun downloadSubtitle(fileId: Int): String? {
        val file = send(TdApi.DownloadFile(fileId, 1, 0, 0, true))
        fileStates[file.id] = file
        val path = file.local.path
        return if (path.isNotEmpty() && File(path).exists()) path else null
    }

    /**
     * v6 subtitles: TDLib's MessageVideo carries NO subtitle field,
     * so sidecars are discovered from the DOCUMENT messages around
     * the video message in the same chat (channels usually post the
     * .srt/.vtt next to the episode). A negative offset centres the
     * GetChatHistory window on the video message (±15 messages);
     * if that call fails, fall back to the video message + 15 older
     * ones. Only documents whose file name ends .srt/.vtt
     * (case-insensitive) count, de-duplicated by file name. A
     * sidecar that fails to download is skipped, never fatal — the
     * video simply gets no CC button for it.
     */
    override suspend fun findSubtitleFiles(chatId: Long, messageId: Long): List<SubtitleTrack> {
        val history = runCatching {
            send(TdApi.GetChatHistory(chatId, messageId, -15, 31, false))
        }.getOrNull() ?: runCatching {
            send(TdApi.GetChatHistory(chatId, messageId, 0, 16, false))
        }.getOrNull() ?: return emptyList()
        val found = LinkedHashMap<String, SubtitleTrack>()
        for (message in history.messages) {
            val content = message.content as? TdApi.MessageDocument ?: continue
            val document = content.document
            val name = document.fileName
            if (name.isBlank()) continue
            val lower = name.lowercase()
            val isVtt = lower.endsWith(".vtt")
            if (!isVtt && !lower.endsWith(".srt")) continue
            if (found.containsKey(lower)) continue
            val localPath = runCatching {
                downloadSubtitle(document.document.id)
            }.getOrNull() ?: continue
            found[lower] = SubtitleTrack(
                fileId = document.document.id,
                fileName = name,
                language = parseSubtitleLanguage(name),
                isVtt = isVtt,
                localPath = localPath
            )
        }
        return found.values.toList()
    }

    /**
     * Language from a sidecar file name: "Ep361.en.srt" -> "en",
     * "Naruto - English.srt" -> "en". A bare "Episode 12.srt" has
     * no language marker and returns null (undetermined — the
     * player still offers it, it just has no language label).
     */
    private fun parseSubtitleLanguage(fileName: String): String? {
        val base = fileName.substringBeforeLast('.')
        val dotLang = base.substringAfterLast('.', "").lowercase()
        if (dotLang.length in 2..3 && dotLang.all { it.isLetter() }) return dotLang
        val lower = base.lowercase()
        return when {
            "english" in lower -> "en"
            "hindi" in lower -> "hi"
            "spanish" in lower || "español" in lower -> "es"
            "french" in lower || "français" in lower -> "fr"
            "german" in lower -> "de"
            "russian" in lower -> "ru"
            "arabic" in lower -> "ar"
            "portuguese" in lower -> "pt"
            "japanese" in lower -> "ja"
            "korean" in lower -> "ko"
            "chinese" in lower -> "zh"
            else -> null
        }
    }

    override suspend fun ensureRange(fileId: Int, offset: Long, length: Long): VideoFileInfo? {
        val file = send(TdApi.DownloadFile(fileId, 32, offset, length, true))
        fileStates[file.id] = file
        return toInfo(file)
    }

    /**
     * v7: free, non-blocking read of the last file state TDLib
     * reported (UpdateFile). The data source polls this while a
     * range download runs instead of issuing a blocking
     * DownloadFile per check.
     */
    override fun getCachedFileState(fileId: Int): VideoFileInfo? =
        fileStates[fileId]?.let { toInfo(it) }

    /**
     * v7: background prefetch — DownloadFile with synchronous =
     * false returns at once with the file's current state while
     * TDLib downloads the range in the background. Progress
     * arrives via UpdateFile into fileStates.
     */
    override suspend fun startRangeDownload(fileId: Int, offset: Long, length: Long): VideoFileInfo? {
        val file = send(TdApi.DownloadFile(fileId, 32, offset, length, false))
        fileStates[file.id] = file
        return toInfo(file)
    }

    private fun toInfo(file: TdApi.File): VideoFileInfo? {
        val path = file.local.path
        if (path.isEmpty()) return null
        return VideoFileInfo(
            fileId = file.id,
            sizeBytes = if (file.size > 0L) file.size else file.expectedSize,
            localPath = path,
            downloadedBytes = file.local.downloadedSize,
            isComplete = file.local.isDownloadingCompleted,
            downloadOffset = file.local.downloadOffset.toLong(),
            downloadedPrefixSize = file.local.downloadedPrefixSize.toLong()
        )
    }

    override fun close() {
        scope.launch {
            try {
                send(TdApi.Close())
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        /** "https://t.me/name/123", "@name", "name" -> "name". */
        fun parseUsername(input: String): String {
            var text = input.trim()
            text = text.removePrefix("https://").removePrefix("http://")
            text = text.removePrefix("t.me/").removePrefix("telegram.me/")
            if (text.startsWith("s/")) text = text.removePrefix("s/")
            if (text.startsWith("c/")) {
                throw IllegalArgumentException(
                    "Private group links are not supported. Use a public group or channel link."
                )
            }
            text = text.removePrefix("@")
            text = text.substringBefore("/").substringBefore("?").trim()
            if (text.isEmpty() || text.length < 3) {
                throw IllegalArgumentException("Enter a valid Telegram group or channel link.")
            }
            return text
        }
    }
}
