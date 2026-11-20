package com.nobi.nobistream2.core

/**
 * Parses video quality from a Telegram post caption, exactly as approved in
 * the NobiStream spec: the caption text is the source of truth.
 *
 * Rules:
 * - "1080p", "1080", "FHD" map to 1080p; "720p", "720", "HD" map to 720p.
 * - "4K", "UHD", "2160p" map to 4K; "1440p", "QHD" map to 1440p.
 * - "480p", "480" map to 480p; "360p", "360" map to 360p.
 * - When several qualities appear in one caption, the highest wins.
 * - When none appears, the result is UNKNOWN (shown only under the All filter).
 */
object QualityParser {

    private val numberedQualityRegex = Regex("""(?i)\b(\d{3,4})\s*p\b""")
    private val bareNumberRegex = Regex("""(?i)\b(2160|1440|1080|720|480|360)\b""")

    /**
     * Quality from the file's real resolution (v2: the source of truth).
     * TDLib reports the video width in pixels; height is not needed because
     * the width alone decides the bucket for 16:9 and 4:3 uploads alike.
     * Returns UNKNOWN when the file carries no dimensions (width <= 0).
     */
    fun fromResolution(width: Int, height: Int): VideoQuality {
        if (width <= 0 && height <= 0) return VideoQuality.UNKNOWN
        // Height alone (portrait video) can still classify: use the larger side.
        val longer = maxOf(width, height)
        return when {
            longer >= 3840 -> VideoQuality.Q2160
            longer >= 1920 -> VideoQuality.Q1080
            longer >= 1280 -> VideoQuality.Q720
            longer >= 854 -> VideoQuality.Q480
            else -> VideoQuality.Q360
        }
    }

    /** Quality from a post caption. Fallback when no resolution is known. */

    fun parseQuality(caption: String?): VideoQuality {
        if (caption.isNullOrBlank()) return VideoQuality.UNKNOWN
        var best = VideoQuality.UNKNOWN

        fun consider(candidate: VideoQuality) {
            if (candidate.rank > best.rank) best = candidate
        }

        for (match in numberedQualityRegex.findAll(caption)) {
            val number = match.groupValues[1].toIntOrNull() ?: continue
            consider(
                when (number) {
                    2160 -> VideoQuality.Q2160
                    1440 -> VideoQuality.Q1440
                    1080 -> VideoQuality.Q1080
                    720 -> VideoQuality.Q720
                    480 -> VideoQuality.Q480
                    360 -> VideoQuality.Q360
                    else -> VideoQuality.UNKNOWN
                }
            )
        }
        val lower = caption.lowercase()
        if (lower.contains("4k") || lower.contains("uhd")) consider(VideoQuality.Q2160)
        if (lower.contains("qhd")) consider(VideoQuality.Q1440)
        if (lower.contains("fhd")) consider(VideoQuality.Q1080)
        // "hd" alone means 720p, but only when no higher quality was found and
        // the caption does not already say fhd/qhd/uhd.
        if (best == VideoQuality.UNKNOWN) {
            if (Regex("""(?i)\bhd\b""").containsMatchIn(caption)) {
                consider(VideoQuality.Q720)
            } else {
                for (match in bareNumberRegex.findAll(caption)) {
                    val number = match.groupValues[1].toIntOrNull() ?: continue
                    consider(
                        when (number) {
                            2160 -> VideoQuality.Q2160
                            1440 -> VideoQuality.Q1440
                            1080 -> VideoQuality.Q1080
                            720 -> VideoQuality.Q720
                            480 -> VideoQuality.Q480
                            360 -> VideoQuality.Q360
                            else -> VideoQuality.UNKNOWN
                        }
                    )
                }
            }
        }
        return best
    }

    /** First non-empty line of the caption, trimmed; falls back to a plain label. */
    fun extractTitle(caption: String?, messageId: Long): String {
        val firstLine = caption
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            .orEmpty()
        if (firstLine.isEmpty()) return "Video $messageId"
        return if (firstLine.length > 120) firstLine.take(117) + "…" else firstLine
    }
}
