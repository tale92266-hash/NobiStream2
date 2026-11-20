package com.nobi.nobistream2.playback

import com.nobi.nobistream2.core.VideoItem

/**
 * In-memory playlist handed from the channel list to the player, so Next /
 * Previous follow exactly the order (and filter) the user was looking at.
 * If the process was killed, the player falls back to reloading the chat.
 */
object PlayerPlaylist {
    var items: List<VideoItem> = emptyList()
    var chatId: Long = 0L

    /**
     * v4-fb3: true when the playlist came from Continue Watching
     * (Home / See all) — a Room-only slice of at most 10 items, NOT
     * the channel's list. The player then expands it in the
     * background to the channel's full filtered list so Next /
     * Previous walk the whole channel instead of stopping at "2 / 2".
     * False for Channel / Search / Library opens, whose playlists
     * already are the list the user was looking at.
     */
    var fromContinue: Boolean = false
}
