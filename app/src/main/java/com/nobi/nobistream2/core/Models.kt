package com.nobi.nobistream2.core

/** Video quality parsed from a post caption. Higher [rank] wins. */
enum class VideoQuality(val label: String, val rank: Int) {
    UNKNOWN("Unknown", 0),
    Q360("360p", 1),
    Q480("480p", 2),
    Q720("720p", 3),
    Q1080("1080p", 4),
    Q1440("1440p", 5),
    Q2160("4K", 6);

    companion object {
        /** Chips shown in the filter row, in display order. */
        val filterOptions: List<VideoQuality> =
            listOf(UNKNOWN, Q480, Q720, Q1080, Q2160)

        fun fromLabel(label: String): VideoQuality =
            values().firstOrNull { it.label.equals(label, ignoreCase = true) } ?: UNKNOWN
    }
}

/** Auth state of the TDLib client, in the order the login flow visits them. */
enum class AuthState {
    UNKNOWN,
    NEED_API_KEYS,
    NEED_PHONE,
    NEED_CODE,
    NEED_PASSWORD,
    READY,
    LOGGED_OUT
}

/** One video message from a Telegram group or channel. */
data class VideoItem(
    val messageId: Long,
    val chatId: Long,
    val fileId: Int,
    val remoteFileId: String,
    val title: String,
    val caption: String,
    val quality: VideoQuality,
    val durationSec: Int,
    val sizeBytes: Long,
    val dateEpochSec: Long,
    val thumbFileId: Int?,
    val thumbLocalPath: String?,
    val progressSec: Long = 0L,
    val watched: Boolean = false,
    /**
     * v5 Smart Filter: how many duplicate copies of this episode were
     * hidden behind this best copy (0 = this video is its own group).
     * Display-only — never persisted, never part of identity.
     */
    val hiddenCopies: Int = 0
) {
    val progressFraction: Float
        get() = if (durationSec > 0) {
            (progressSec.toFloat() / durationSec.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/** One saved Telegram group or channel shown on Home. */
data class GroupItem(
    val chatId: Long,
    val username: String,
    val title: String,
    val photoLocalPath: String?,
    val videoCount: Int,
    val lastUpdatedEpochSec: Long
)

/** The signed-in Telegram user, shown in Settings / Account. Only the
 * user's own account info, shown to themselves — never any code, secret
 * or identifier beyond the name, @username and phone Telegram itself
 * shows in its own Settings. */
data class TelegramUser(
    val displayName: String,
    val username: String?,
    val phoneNumber: String?
)
