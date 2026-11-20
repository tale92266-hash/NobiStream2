package com.nobi.nobistream2.core

/**
 * v5 Smart Filter — one episode, one card.
 *
 * A channel often posts the SAME episode 3-4 times in different
 * qualities (480p / 720p / 1080p / 4K), so the list showed the same
 * episode four times and Next replayed it in another quality. The
 * Smart Filter groups those copies by episode and keeps only the
 * BEST copy of each episode in the list; the rest are hidden behind
 * a "N copies" badge the user can tap to pick any copy manually.
 *
 * Rules (spec approved 2026-11-17):
 * - Identity = season + episode number parsed from title/caption.
 *   "Naruto Shippuden [S16 Ep 13] Ep 361" is season 16, episode 361.
 *   "Ep 361" and "Episode 361" are the same episode. When several
 *   episode numbers appear, the LAST one in the text wins — the
 *   absolute number conventionally comes last ("[S16 Ep 13] Ep 361"
 *   is episode 361, not 13), so a repost titled just "Ep 361" still
 *   groups with it.
 * - No episode number found = the video is standalone, never merged.
 * - "Part 1" / "Part 2" of one episode are DIFFERENT parts, not
 *   duplicates — the part number is part of the key, so both stay.
 * - Season must match exactly (both absent counts as a match). A
 *   copy with no season marker does not merge with one that has
 *   "S16" — different seasons can reuse episode numbers.
 * - Best copy: highest quality rank first (4K > 1080p > 720p >
 *   480p > 360p > Unknown), then larger file (better bitrate proxy),
 *   then newest post.
 * - Grouping is in-memory over the loaded list; Load More merges new
 *   pages into the same groups, so a duplicate never reappears.
 */
data class EpisodeKey(
    val chatId: Long,
    val season: Int?,
    val episode: Int,
    val part: Int?
)

/** One episode and all its posted copies, best copy first. */
data class EpisodeGroup(
    val key: EpisodeKey?,
    val best: VideoItem,
    val copies: List<VideoItem>
) {
    /** Copies hidden behind the best copy's card (0 = no duplicates). */
    val hiddenCount: Int get() = (copies.size - 1).coerceAtLeast(0)
}

object SmartFilter {

    private val seasonRegex =
        Regex("""(?i)(?:\bS(\d{1,2})\b|\bSeason\s+(\d{1,2})\b)""")
    private val episodeRegex =
        Regex("""(?i)(?:\b(?:Ep\.?|Episode|E)\s*[-:#]?\s*(\d{1,4})\b|S\d{1,2}E(\d{1,4})\b)""")
    private val compactSeasonEpisodeRegex =
        Regex("""(?i)\bS(\d{1,2})E(\d{1,4})\b""")
    private val partRegex = Regex("""(?i)\bPart\s*(\d{1,2})\b""")

    /** Best copy first: quality rank, then file size, then newest. */
    private val bestFirst = compareByDescending<VideoItem> { it.quality.rank }
        .thenByDescending { it.sizeBytes }
        .thenByDescending { it.dateEpochSec }

    /** Episode identity of one video, or null when it has no episode number. */
    fun parseKey(item: VideoItem): EpisodeKey? {
        val text = "${item.title}\n${item.caption}"
        // (position in text, episode number) from every marker form.
        val numbered = mutableListOf<Pair<Int, Int>>()
        episodeRegex.findAll(text).forEach { match ->
            val number = match.groupValues.getOrNull(1)?.toIntOrNull()
                ?: match.groupValues.getOrNull(2)?.toIntOrNull()
            if (number != null) numbered.add(match.range.first to number)
        }
        compactSeasonEpisodeRegex.findAll(text).forEach { match ->
            match.groupValues.getOrNull(2)?.toIntOrNull()?.let { number ->
                numbered.add(match.range.first to number)
            }
        }
        // Last marker in the text wins (the absolute number comes last).
        val episode = numbered.maxByOrNull { it.first }?.second ?: return null
        val compact = compactSeasonEpisodeRegex.find(text)
        val season = compact?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: seasonRegex.find(text)?.let { match ->
                match.groupValues.getOrNull(1)?.toIntOrNull()
                    ?: match.groupValues.getOrNull(2)?.toIntOrNull()
            }
        val part = partRegex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return EpisodeKey(item.chatId, season, episode, part)
    }

    /**
     * v1.1.1 (ported from NobiStream 1 v2.6.1): posts that carry a video
     * but are NOT an episode — e.g. "BIG NEWS FOR NARUTO FANS!" — go in
     * their own "Other videos" section instead of polluting the episode
     * list. A post is non-episode when it has no episode key AND no
     * episode-shaped marker at all. Conservative: anything shaped like
     * an episode (season/episode/part/quality marker) stays an episode.
     */
    private val nonEpisodeRegex =
        Regex("""(?i)(season\s*\d+|s\d{1,2}e\d{1,4}|ep\.?|episode|part\s*\d|1080p|720p|480p|360p|4k)""")

    fun isNonEpisode(item: VideoItem): Boolean {
        if (parseKey(item) != null) return false
        val text = "${item.title} ${item.caption}".trim()
        if (text.isEmpty()) return false
        return !nonEpisodeRegex.containsMatchIn(text)
    }

    /**
     * v1.1.1 (ported from NobiStream 1 v2.6.1): one episode watched in
     * two qualities must not appear twice in Continue Watching. Keep
     * the best copy per episode key; standalones pass through as-is.
     */
    fun dedupContinue(items: List<VideoItem>): List<VideoItem> {
        val seen = HashSet<EpisodeKey>()
        val out = ArrayList<VideoItem>(items.size)
        for (item in items) {
            val key = parseKey(item)
            if (key == null) {
                out.add(item)
            } else if (seen.add(key)) {
                out.add(item)
            }
        }
        return out
    }

    /** Regression guard: the same episode key must never produce two groups. */
    fun hasDuplicateEpisodeKeys(groups: List<EpisodeGroup>): Boolean {
        val seen = HashSet<EpisodeKey>()
        for (group in groups) {
            val key = group.key ?: continue
            if (!seen.add(key)) return true
        }
        return false
    }

    /**
     * v1.1.1: tap-time best-copy resolution. The tapped item may be any
     * copy (e.g. the 360p card in the grid); the player must open the
     * group's BEST copy (highest quality rank, then largest file, then
     * newest) with the deduped list as the playlist. Falls back to the
     * tapped item itself when it has no group.
     */
    fun bestCopyFor(item: VideoItem, groups: List<EpisodeGroup>): VideoItem {
        for (group in groups) {
            if (group.copies.any { it.messageId == item.messageId && it.chatId == item.chatId }) {
                return group.best
            }
        }
        return item
    }

    /**
     * Group a loaded list into episodes. Videos without an episode
     * number each get their own group. Groups are ordered by where
     * their BEST copy sits in the original list, so a newest-first
     * channel list stays newest-first after dedup.
     */
    fun groupEpisodes(items: List<VideoItem>): List<EpisodeGroup> {
        val byKey = LinkedHashMap<Any, MutableList<VideoItem>>()
        for (item in items) {
            val key = parseKey(item)
            // Null key = standalone: one group per video, never merged.
            val mapKey: Any = key ?: "standalone:${item.chatId}:${item.messageId}"
            byKey.getOrPut(mapKey) { mutableListOf() }.add(item)
        }
        val indexOf = HashMap<Pair<Long, Long>, Int>(items.size)
        items.forEachIndexed { index, video ->
            indexOf[video.chatId to video.messageId] = index
        }
        return byKey.values.map { members ->
            val sorted = members.sortedWith(bestFirst)
            EpisodeGroup(parseKey(sorted[0]), sorted[0], sorted)
        }.sortedBy { group ->
            indexOf[group.best.chatId to group.best.messageId] ?: Int.MAX_VALUE
        }
    }

    /**
     * The deduped list: one best copy per episode, in channel order,
     * each carrying its hidden-copy count for the card badge.
     */
    fun dedup(items: List<VideoItem>): List<VideoItem> =
        groupEpisodes(items).map { group ->
            group.best.copy(hiddenCopies = group.hiddenCount)
        }
}
