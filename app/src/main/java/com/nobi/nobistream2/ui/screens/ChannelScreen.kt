package com.nobi.nobistream2.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nobi.nobistream2.core.EpisodeGroup
import com.nobi.nobistream2.core.FormatUtils
import com.nobi.nobistream2.core.SmartFilter
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.core.VideoQuality
import com.nobi.nobistream2.data.NobiRepository
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.components.AppTopBar
import com.nobi.nobistream2.ui.components.CopiesSheet
import com.nobi.nobistream2.ui.components.EmptyState
import com.nobi.nobistream2.ui.components.ErrorState
import com.nobi.nobistream2.ui.components.LoadMoreFooter
import com.nobi.nobistream2.ui.components.QualityChip
import com.nobi.nobistream2.ui.components.SkeletonGridCard
import com.nobi.nobistream2.ui.components.SkeletonRow
import com.nobi.nobistream2.ui.components.VideoCard
import kotlinx.coroutines.launch
import com.nobi.nobistream2.ui.components.VideoThumb
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens

/**
 * Channel (Appendix A6/A9/A10): filter chips, sort sheet, filter sheet,
 * list/grid toggle, paginated loading, Smart Filter dedup, session cache
 * (count correct from the first frame — the 2.6.3 fix carried over).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    vm: AppViewModel,
    chatId: Long,
    onBack: () -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>, Long) -> Unit,
    onOpenSearch: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val groups by vm.groups.collectAsState()
    val group = groups.firstOrNull { it.chatId == chatId }
    val sortMode by vm.repository.settingsStore.sortMode.collectAsState(initial = "Newest")
    val viewMode by vm.repository.settingsStore.viewMode.collectAsState(initial = "List")
    val smartOn by vm.repository.settingsStore.smartFilter.collectAsState(initial = true)

    var allVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    var lastMessageId by remember { mutableStateOf(0L) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreFailed by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showOnlyWatched by remember { mutableStateOf(false) }
    var showOnlyWatchLater by remember { mutableStateOf(false) }
    var actionItem by remember { mutableStateOf<VideoItem?>(null) }
    var copiesSheetFor by remember { mutableStateOf<EpisodeGroup?>(null) }
    var watchLaterIds by remember { mutableStateOf<Set<Pair<Long, Long>>>(emptySet()) }

    // First page, cached in state so the count is correct from frame one.
    LaunchedEffect(chatId) {
        loading = true
        loadError = false
        val page = runCatching { vm.repository.loadVideos(chatId, 0L, 50) }.getOrNull()
        if (page == null) {
            loadError = true
        } else {
            allVideos = page.videos
            totalCount = page.totalCount
            lastMessageId = page.lastMessageId
            hasMore = page.rawCount >= 50 && page.lastMessageId > 0L
        }
        loading = false
    }
    val watchLaterList by vm.watchLater.collectAsState()
    LaunchedEffect(watchLaterList) {
        watchLaterIds = watchLaterList.map { it.chatId to it.messageId }.toSet()
    }

    fun loadMore() {
        if (loadingMore || !hasMore || loading) return
        loadingMore = true
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val page = runCatching { vm.repository.loadVideos(chatId, lastMessageId, 50) }.getOrNull()
            if (page != null) {
                allVideos = allVideos + page.videos
                lastMessageId = page.lastMessageId
                hasMore = page.rawCount >= 50 && page.lastMessageId > 0L
                loadMoreFailed = false
            } else {
                loadMoreFailed = true
            }
            loadingMore = false
        }
    }

    // Dedup first, then quality filter — same order as the old app.
    // v1.1.1 item 11: keep the GROUPS so a tap resolves to the group's
    // BEST copy (highest quality) instead of the tapped copy.
    val episodeGroups = remember(allVideos, smartOn) {
        if (smartOn) SmartFilter.groupEpisodes(allVideos)
        else allVideos.map { EpisodeGroup(null, it, listOf(it)) }
    }
    val deduped = remember(episodeGroups) { episodeGroups.map { it.best.copy(hiddenCopies = it.hiddenCount) } }
    // v1.1.1 item 11 (NobiStream 1 v2.6.1): non-episode posts go in
    // their own "Other videos" section instead of the episode list.
    val otherVideos = remember(deduped, smartOn) {
        if (smartOn) deduped.filter { SmartFilter.isNonEpisode(it) } else emptyList()
    }
    val episodeVideos = remember(deduped, otherVideos) { deduped - otherVideos.toSet() }
    fun applyListFilters(list: List<VideoItem>): List<VideoItem> {
        var out = list
        if (filter != "All") out = out.filter { it.quality.label == filter }
        if (showOnlyWatched) out = out.filter { it.watched }
        if (showOnlyWatchLater) out = out.filter { watchLaterIds.contains(it.chatId to it.messageId) }
        if (query.isNotBlank()) {
            out = out.filter { it.title.contains(query, ignoreCase = true) || it.caption.contains(query, ignoreCase = true) }
        }
        return when (sortMode) {
            "Oldest" -> out.sortedBy { it.dateEpochSec }
            "TitleAz" -> out.sortedBy { it.title.lowercase() }
            "Largest" -> out.sortedByDescending { it.sizeBytes }
            "HighestQuality" -> out.sortedByDescending { it.quality.rank }
            else -> out.sortedByDescending { it.dateEpochSec }
        }
    }
    val filtered = remember(episodeVideos, filter, query, showOnlyWatched, showOnlyWatchLater, watchLaterIds, sortMode) {
        applyListFilters(episodeVideos)
    }
    val otherFiltered = remember(otherVideos, filter, query, showOnlyWatched, showOnlyWatchLater, watchLaterIds, sortMode) {
        applyListFilters(otherVideos)
    }
    // v1.1.1 item 11: tap = the best copy VISIBLE in the current list
    // (the group's best, unless the quality filter hides it — then the
    // best visible copy). The playlist stays the current filtered list.
    fun playBest(item: VideoItem) {
        val group = episodeGroups.firstOrNull { g ->
            g.copies.any { it.messageId == item.messageId && it.chatId == item.chatId }
        }
        val inOther = otherFiltered.any { it.messageId == item.messageId }
        val visibleList = if (inOther) otherFiltered else filtered
        val best = group?.copies?.firstOrNull { c ->
            visibleList.any { it.messageId == c.messageId }
        } ?: item
        onPlayVideo(best, visibleList, chatId)
    }
    fun groupFor(item: VideoItem): EpisodeGroup? =
        episodeGroups.firstOrNull { g ->
            g.copies.any { it.messageId == item.messageId && it.chatId == item.chatId }
        }
    // v1.1.1 item 11: play a copy the user picked explicitly (copies
    // sheet). A non-best copy is not in the deduped list, so it is
    // swapped in for its group's best — the playlist order is kept.
    fun playCopy(copy: VideoItem) {
        val idx = filtered.indexOfFirst { it.messageId == copy.messageId }
        if (idx >= 0) {
            onPlayVideo(copy, filtered, chatId)
            return
        }
        val group = episodeGroups.firstOrNull { g ->
            g.copies.any { it.messageId == copy.messageId && it.chatId == copy.chatId }
        }
        val bestIdx = group?.let { g -> filtered.indexOfFirst { it.messageId == g.best.messageId } } ?: -1
        if (bestIdx >= 0) {
            val list = filtered.toMutableList()
            list[bestIdx] = copy
            onPlayVideo(copy, list, chatId)
        } else {
            onPlayVideo(copy, listOf(copy), chatId)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = group?.title ?: "Channel",
            subtitle = "Episodes (${filtered.size})" + if (totalCount > 0) " · $totalCount on Telegram" else "",
            onBack = onBack,
            actions = {
                IconButton(onClick = onOpenSearch, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = NsColors.TextPrimary)
                }
                IconButton(onClick = { showSort = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Sort, contentDescription = "Sort", tint = NsColors.TextPrimary)
                }
                IconButton(onClick = { showFilter = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Tune, contentDescription = "Filter", tint = NsColors.TextPrimary)
                }
                IconButton(
                    onClick = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            vm.repository.settingsStore.setViewMode(if (viewMode == "List") "Grid" else "List")
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (viewMode == "List") Icons.Filled.GridView else Icons.Filled.ViewList,
                        contentDescription = "Toggle list/grid",
                        tint = NsColors.TextPrimary
                    )
                }
            }
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search by title or episode") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = NsDimens.ContentPadding).height(NsDimens.InputHeight)
        )
        Spacer(Modifier.height(8.dp))
        // v1.1.1 item 8: the 5 chips overflow a narrow screen — the row
        // scrolls horizontally now (and QualityChip itself never wraps).
        Row(
            modifier = Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = NsDimens.ContentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "480p", "720p", "1080p", "4K").forEach { label ->
                QualityChip(label = label, selected = filter == label, onClick = { filter = label })
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            loading -> {
                // v1.1.1 item 7: first-page skeleton matches the mode —
                // grid skeletons in grid mode, list skeletons in list mode.
                if (viewMode == "Grid") {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = NsDimens.ContentPadding, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(6) { SkeletonGridCard() }
                    }
                } else {
                    Column(modifier = Modifier.padding(horizontal = NsDimens.ContentPadding)) {
                        repeat(4) { SkeletonRow() }
                    }
                }
            }
            loadError -> {
                ErrorState(
                    title = "Couldn't load videos",
                    body = "Check your connection and try again. If the group was deleted or made private, it may no longer open here.",
                    onRetry = {
                        loading = true
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            val page = runCatching { vm.repository.loadVideos(chatId, 0L, 50) }.getOrNull()
                            if (page != null) {
                                allVideos = page.videos; totalCount = page.totalCount
                                lastMessageId = page.lastMessageId; hasMore = page.rawCount >= 50
                                loadError = false
                            }
                            loading = false
                        }
                    }
                )
            }
            filtered.isEmpty() && otherFiltered.isEmpty() -> {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "No videos found",
                    subtitle = "Try a different filter, or tap Refresh to load the latest videos."
                )
            }
            viewMode == "Grid" -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = NsDimens.ContentPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.chatId to it.messageId }) { video ->
                        ChannelGridCard(
                            repo = vm.repository,
                            video = video,
                            onPlay = { playBest(video) },
                            onCopiesClick = if (video.hiddenCopies > 0) {
                                { copiesSheetFor = groupFor(video) }
                            } else null
                        )
                    }
                    if (otherFiltered.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Text(
                                "Other videos (${otherFiltered.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = NsColors.TextPrimary,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                        items(otherFiltered, key = { it.chatId to it.messageId }) { video ->
                            ChannelGridCard(
                                repo = vm.repository,
                                video = video,
                                onPlay = { playBest(video) },
                                onCopiesClick = null
                            )
                        }
                    }
                    if (hasMore) {
                        item(span = { GridItemSpan(2) }) {
                            LoadMoreFooter(
                                loadingMore = loadingMore,
                                failed = loadMoreFailed,
                                onLoadMore = { loadMore() }
                            )
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = NsDimens.ContentPadding, end = NsDimens.ContentPadding, top = 8.dp, bottom = 16.dp)
                ) {
                    items(filtered, key = { it.chatId to it.messageId }) { video ->
                        VideoCard(
                            repo = vm.repository,
                            item = video,
                            onClick = { playBest(video) },
                            onLongClick = { actionItem = video }
                        )
                    }
                    if (otherFiltered.isNotEmpty()) {
                        item {
                            Text(
                                "Other videos (${otherFiltered.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = NsColors.TextPrimary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(otherFiltered, key = { it.chatId to it.messageId }) { video ->
                            VideoCard(
                                repo = vm.repository,
                                item = video,
                                onClick = { playBest(video) },
                                onLongClick = { actionItem = video }
                            )
                        }
                    }
                    if (hasMore) {
                        item {
                            LoadMoreFooter(
                                loadingMore = loadingMore,
                                failed = loadMoreFailed,
                                onLoadMore = { loadMore() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSort) {
        SortSheet(
            current = sortMode,
            onSelect = { mode ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    vm.repository.settingsStore.setSortMode(mode)
                }
                showSort = false
            },
            onDismiss = { showSort = false }
        )
    }
    if (showFilter) {
        FilterSheet(
            onlyWatched = showOnlyWatched,
            onlyWatchLater = showOnlyWatchLater,
            onWatchedChange = { showOnlyWatched = it },
            onWatchLaterChange = { showOnlyWatchLater = it },
            onClear = { showOnlyWatched = false; showOnlyWatchLater = false; filter = "All" },
            onDismiss = { showFilter = false }
        )
    }
    actionItem?.let { item ->
        VideoActionSheet(
            vm = vm,
            item = item,
            onPlay = { playBest(item); actionItem = null },
            onPlayFromStart = {
                // Play from start: clear the saved position first, then play.
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    vm.repository.savePosition(item, 0L)
                    playBest(item)
                    actionItem = null
                }
            },
            onChooseCopy = if (item.hiddenCopies > 0) {
                { copiesSheetFor = groupFor(item); actionItem = null }
            } else null,
            onDismiss = { actionItem = null },
            onWatchLaterToggled = { watchLaterIds = watchLaterIds.toMutableSet().also { /* refreshed on next open */ } }
        )
    }
    copiesSheetFor?.let { group ->
        CopiesSheet(
            copies = group.copies,
            onPick = { copy -> copiesSheetFor = null; playCopy(copy) },
            onDismiss = { copiesSheetFor = null }
        )
    }
}

/** Appendix A10: sort sheet — single selection, check mark, persisted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf("Newest" to "Newest first", "Oldest" to "Oldest first", "TitleAz" to "Title A–Z", "Largest" to "Largest file", "HighestQuality" to "Highest quality")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Sort order", style = MaterialTheme.typography.titleLarge, color = NsColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            options.forEach { (mode, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onSelect(mode) }, modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = NsColors.TextPrimary)
                    }
                    if (current == mode) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = NsColors.Primary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Appendix A9: filter sheet — instant local filters, no Apply needed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    onlyWatched: Boolean,
    onlyWatchLater: Boolean,
    onWatchedChange: (Boolean) -> Unit,
    onWatchLaterChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Filter", style = MaterialTheme.typography.titleLarge, color = NsColors.TextPrimary)
                TextButton(onClick = onClear) { Text("Clear All") }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = onlyWatched, onCheckedChange = onWatchedChange)
                Text("Watched only", style = MaterialTheme.typography.bodyLarge, color = NsColors.TextPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = onlyWatchLater, onCheckedChange = onWatchLaterChange)
                Text("Watch Later only", style = MaterialTheme.typography.bodyLarge, color = NsColors.TextPrimary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Appendix A7: video action sheet — Play, Play from Start, Watch Later, Download, Share, Details. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionSheet(
    vm: AppViewModel,
    item: VideoItem,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onDismiss: () -> Unit,
    onWatchLaterToggled: () -> Unit,
    onChooseCopy: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isSaved by remember { mutableStateOf(false) }
    LaunchedEffect(item) { isSaved = vm.isWatchLater(item) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = NsColors.TextPrimary, maxLines = 2)
            Text(
                "${item.quality.label} · ${FormatUtils.formatDuration(item.durationSec)} · ${FormatUtils.formatSize(item.sizeBytes)} · ${FormatUtils.formatDate(item.dateEpochSec)}",
                style = MaterialTheme.typography.bodySmall,
                color = NsColors.TextSecondary
            )
            Spacer(Modifier.height(16.dp))
            ActionRow(Icons.Filled.PlayArrow, "Play") { onPlay() }
            ActionRow(Icons.Filled.PlayArrow, "Play from Start") { onPlayFromStart() }
            // v1.1.1 item 11: pick a specific copy (the card tap plays
            // the best copy by default; this is the manual override).
            if (item.hiddenCopies > 0 && onChooseCopy != null) {
                ActionRow(Icons.Filled.CopyAll, "Choose copy (${item.hiddenCopies + 1})") { onChooseCopy() }
            }
            ActionRow(
                if (isSaved) Icons.Filled.BookmarkRemove else Icons.Filled.BookmarkAdd,
                if (isSaved) "Remove from Watch Later" else "Add to Watch Later"
            ) {
                vm.toggleWatchLater(item)
                isSaved = !isSaved
                onWatchLaterToggled()
            }
            ActionRow(Icons.Filled.Download, "Download") {
                vm.startDownload(item)
                onDismiss()
            }
            ActionRow(Icons.Filled.Share, "Share") {
                // Share title + source link: real Android share sheet via context.
                val text = "${item.title}\nhttps://t.me/${item.chatId}"
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                context.startActivity(
                    android.content.Intent.createChooser(intent, "Share video")
                )
                onDismiss()
            }
            ActionRow(Icons.Filled.Info, "Details") { /* details are already shown above */ }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = label, tint = NsColors.TextPrimary)
        }
        TextButton(onClick = onClick) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = NsColors.TextPrimary)
        }
    }
}

/**
 * v1.1.1 items 8/11: grid card — 16/9 thumb + copies badge (tap the
 * badge to pick a copy) + title + quality · duration meta. Grid gaps
 * are 14dp (HTML rail gap).
 */
@Composable
private fun ChannelGridCard(
    repo: NobiRepository,
    video: VideoItem,
    onPlay: () -> Unit,
    onCopiesClick: (() -> Unit)?
) {
    Column(modifier = Modifier.clickable(onClick = onPlay)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            VideoThumb(repo, video, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            if (video.hiddenCopies > 0) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NsColors.BadgeBg)
                        .then(if (onCopiesClick != null) Modifier.clickable(onClick = onCopiesClick) else Modifier)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${video.hiddenCopies + 1} copies",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(video.title, style = MaterialTheme.typography.titleSmall, color = NsColors.TextPrimary, maxLines = 2)
        Text(
            "${video.quality.label} · ${FormatUtils.formatDuration(video.durationSec)}",
            style = MaterialTheme.typography.bodySmall,
            color = NsColors.TextSecondary
        )
    }
}

// v1.1.1 item 7: LoadMoreFooter lives in ui.components (shared with LibraryScreen).
