package com.nobi.nobistream2.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BookmarkRemove
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
import androidx.compose.ui.unit.dp
import com.nobi.nobistream2.core.FormatUtils
import com.nobi.nobistream2.core.SmartFilter
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.core.VideoQuality
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.components.AppTopBar
import com.nobi.nobistream2.ui.components.EmptyState
import com.nobi.nobistream2.ui.components.ErrorState
import com.nobi.nobistream2.ui.components.QualityChip
import com.nobi.nobistream2.ui.components.SkeletonRow
import com.nobi.nobistream2.ui.components.VideoCard
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
    var loadError by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showOnlyWatched by remember { mutableStateOf(false) }
    var showOnlyWatchLater by remember { mutableStateOf(false) }
    var actionItem by remember { mutableStateOf<VideoItem?>(null) }
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
            }
            loadingMore = false
        }
    }

    // Dedup first, then quality filter — same order as the old app.
    val deduped = remember(allVideos, smartOn) {
        if (smartOn) SmartFilter.dedup(allVideos) else allVideos
    }
    val filtered = remember(deduped, filter, query, showOnlyWatched, showOnlyWatchLater, watchLaterIds) {
        var list = deduped
        if (filter != "All") list = list.filter { it.quality.label == filter }
        if (showOnlyWatched) list = list.filter { it.watched }
        if (showOnlyWatchLater) list = list.filter { watchLaterIds.contains(it.chatId to it.messageId) }
        if (query.isNotBlank()) {
            list = list.filter { it.title.contains(query, ignoreCase = true) || it.caption.contains(query, ignoreCase = true) }
        }
        when (sortMode) {
            "Oldest" -> list.sortedBy { it.dateEpochSec }
            "TitleAz" -> list.sortedBy { it.title.lowercase() }
            "Largest" -> list.sortedByDescending { it.sizeBytes }
            "HighestQuality" -> list.sortedByDescending { it.quality.rank }
            else -> list.sortedByDescending { it.dateEpochSec }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = group?.title ?: "Channel",
            subtitle = "Episodes (${filtered.size})" + if (totalCount > 0) " · $totalCount on Telegram" else "",
            onBack = onBack,
            actions = {
                IconButton(onClick = onOpenSearch, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = NsColors.TextPrimary)
                }
                IconButton(onClick = { showSort = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Sort, contentDescription = "Sort", tint = NsColors.TextPrimary)
                }
                IconButton(onClick = { showFilter = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Tune, contentDescription = "Filter", tint = NsColors.TextPrimary)
                }
                IconButton(
                    onClick = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            vm.repository.settingsStore.setViewMode(if (viewMode == "List") "Grid" else "List")
                        }
                    },
                    modifier = Modifier.size(48.dp)
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NsDimens.ContentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "480p", "720p", "1080p", "4K").forEach { label ->
                QualityChip(label = label, selected = filter == label, onClick = { filter = label })
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            loading -> {
                Column(modifier = Modifier.padding(horizontal = NsDimens.ContentPadding)) {
                    repeat(4) { SkeletonRow() }
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
            filtered.isEmpty() -> {
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.chatId to it.messageId }) { video ->
                        Column(
                            modifier = Modifier.clickable { onPlayVideo(video, filtered, chatId) }
                        ) {
                            VideoThumb(vm.repository, video, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                            Spacer(Modifier.height(4.dp))
                            Text(video.title, style = MaterialTheme.typography.titleSmall, color = NsColors.TextPrimary, maxLines = 2)
                            Text("${video.quality.label} · ${FormatUtils.formatDuration(video.durationSec)}", style = MaterialTheme.typography.bodySmall, color = NsColors.TextSecondary)
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = NsDimens.ContentPadding, bottom = 16.dp)
                ) {
                    items(filtered, key = { it.chatId to it.messageId }) { video ->
                        VideoCard(
                            repo = vm.repository,
                            item = video,
                            onClick = { onPlayVideo(video, filtered, chatId) },
                            onLongClick = { actionItem = video }
                        )
                    }
                    if (hasMore) {
                        item {
                            LaunchedEffect(Unit) { loadMore() }
                            if (loadingMore) {
                                Text("Loading more videos…", style = MaterialTheme.typography.bodyMedium, color = NsColors.TextSecondary, modifier = Modifier.padding(vertical = 16.dp))
                            }
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
            onPlay = { onPlayVideo(item, filtered, chatId); actionItem = null },
            onPlayFromStart = {
                // Play from start: clear the saved position first, then play.
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    vm.repository.savePosition(item, 0L)
                    onPlayVideo(item, filtered, chatId)
                    actionItem = null
                }
            },
            onDismiss = { actionItem = null },
            onWatchLaterToggled = { watchLaterIds = watchLaterIds.toMutableSet().also { /* refreshed on next open */ } }
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
    onWatchLaterToggled: () -> Unit
) {
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
                androidx.compose.ui.platform.LocalContext.current.startActivity(
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
