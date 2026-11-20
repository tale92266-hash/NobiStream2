package com.nobi.nobistream2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobi.nobistream2.core.EpisodeGroup
import com.nobi.nobistream2.core.FormatUtils
import com.nobi.nobistream2.core.SmartFilter
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.core.VideoQuality
import com.nobi.nobistream2.data.db.DownloadEntity
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.components.AddGroupSheet
import com.nobi.nobistream2.ui.components.CopiesSheet
import com.nobi.nobistream2.ui.components.EpisodeCard
import com.nobi.nobistream2.ui.components.LoadMoreFooter
import com.nobi.nobistream2.ui.components.NsTopBar
import com.nobi.nobistream2.ui.components.PrimaryButton
import com.nobi.nobistream2.ui.components.QualityChip
import com.nobi.nobistream2.ui.components.SkeletonRow
import com.nobi.nobistream2.ui.components.VideoCard
import com.nobi.nobistream2.ui.theme.ManropeFamily
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens
import com.nobi.nobistream2.ui.theme.NsIcons
import com.nobi.nobistream2.ui.theme.SoraFamily
import kotlinx.coroutines.launch

/**
 * Library (HTML redesign v1.1.0): "Your Library" + real counts, sort
 * icon-btn, quality chips that REALLY filter, HTML episode cards with
 * centre play FAB + copies badge, filtered-empty box. Watch Later and
 * Downloads live INSIDE Library as top sections (no 5th tab, no feature
 * deleted). Data is real: deduped episodes via SmartFilter, real
 * thumbnails, real dates/sizes/handles.
 */
@Composable
fun LibraryScreen(
    vm: AppViewModel,
    onOpenGroup: (Long) -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>, Long) -> Unit,
    onPlayDownloaded: (DownloadEntity) -> Unit
) {
    val groups by vm.groups.collectAsState()
    val sortMode by vm.repository.settingsStore.sortMode.collectAsState(initial = "Newest")
    val smartOn by vm.repository.settingsStore.smartFilter.collectAsState(initial = true)
    val defaultFilter by vm.repository.settingsStore.defaultFilter.collectAsState(initial = "All")
    var section by remember { mutableStateOf("Episodes") }
    var filter by remember(defaultFilter) { mutableStateOf(defaultFilter) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var actionItem by remember { mutableStateOf<VideoItem?>(null) }
    var copiesSheetFor by remember { mutableStateOf<EpisodeGroup?>(null) }

    var allVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var cursors by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreFailed by remember { mutableStateOf(false) }

    LaunchedEffect(groups) {
        if (groups.isEmpty()) {
            allVideos = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val page = runCatching { vm.repository.loadLibraryPage(groups, emptyMap(), 50) }.getOrNull()
        if (page != null) {
            allVideos = page.videos
            cursors = page.cursors
            hasMore = page.hasMore
        }
        loading = false
    }

    fun loadMore() {
        if (loadingMore || !hasMore || loading) return
        loadingMore = true
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val page = runCatching { vm.repository.loadLibraryPage(groups, cursors, 50) }.getOrNull()
            if (page != null) {
                allVideos = allVideos + page.videos
                cursors = page.cursors
                hasMore = page.hasMore
                loadMoreFailed = false
            } else {
                loadMoreFailed = true
            }
            loadingMore = false
        }
    }

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
        return when (sortMode) {
            "Oldest" -> out.sortedBy { it.dateEpochSec }
            "TitleAz" -> out.sortedBy { it.title.lowercase() }
            "Largest" -> out.sortedByDescending { it.sizeBytes }
            "HighestQuality" -> out.sortedByDescending { it.quality.rank }
            else -> out.sortedByDescending { it.dateEpochSec }
        }
    }
    val filtered = remember(episodeVideos, filter, sortMode) {
        applyListFilters(episodeVideos)
    }
    val otherFiltered = remember(otherVideos, filter, sortMode) {
        applyListFilters(otherVideos)
    }
    fun groupFor(item: VideoItem): EpisodeGroup? =
        episodeGroups.firstOrNull { g ->
            g.copies.any { it.messageId == item.messageId && it.chatId == item.chatId }
        }
    // v1.1.1 item 11: tap = the best copy VISIBLE in the current list
    // (the group's best, unless the quality filter hides it — then the
    // best visible copy). The playlist stays the current filtered list.
    fun playBest(item: VideoItem) {
        val group = groupFor(item)
        val inOther = otherFiltered.any { it.messageId == item.messageId }
        val visibleList = if (inOther) otherFiltered else filtered
        val best = group?.copies?.firstOrNull { c ->
            visibleList.any { it.messageId == c.messageId }
        } ?: item
        onPlayVideo(best, visibleList, item.chatId)
    }
    // v1.1.1 item 11: play a copy the user picked explicitly (copies
    // sheet). A non-best copy is not in the deduped list, so it is
    // swapped in for its group's best — the playlist order is kept.
    fun playCopy(copy: VideoItem) {
        val idx = filtered.indexOfFirst { it.messageId == copy.messageId }
        if (idx >= 0) {
            onPlayVideo(copy, filtered, copy.chatId)
            return
        }
        val group = groupFor(copy)
        val bestIdx = group?.let { g -> filtered.indexOfFirst { it.messageId == g.best.messageId } } ?: -1
        if (bestIdx >= 0) {
            val list = filtered.toMutableList()
            list[bestIdx] = copy
            onPlayVideo(copy, list, copy.chatId)
        } else {
            onPlayVideo(copy, listOf(copy), copy.chatId)
        }
    }
    val groupById = remember(groups) { groups.associateBy { it.chatId } }
    val totalVideos = groups.sumOf { it.videoCount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NsColors.Background)
            .padding(horizontal = NsDimens.ContentPadding)
    ) {
        NsTopBar(onAdd = { showAddSheet = true }, onRefresh = { vm.refreshGroups() })
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Your Library",
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    color = NsColors.TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "$totalVideos videos · ${groups.size} ${if (groups.size == 1) "group" else "groups"} · Smart Filter ${if (smartOn) "on" else "off"}",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = NsColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NsColors.Surface)
                    .border(1.dp, NsColors.Divider, CircleShape)
                    .clickable { showSort = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(NsIcons.Sliders, contentDescription = "Sort", tint = NsColors.TextPrimary, modifier = Modifier.size(19.dp))
            }
        }

        // Sections: Episodes / Watch Later / Downloads (inside Library, per spec §9).
        // v1.1.1: the row scrolls so chips never squeeze or wrap.
        Row(
            modifier = Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Episodes", "Watch Later", "Downloads").forEach { label ->
                QualityChip(label = label, selected = section == label, onClick = { section = label })
            }
        }

        when (section) {
            "Watch Later" -> WatchLaterSection(vm = vm, onPlayVideo = onPlayVideo)
            "Downloads" -> DownloadsSection(vm = vm, onPlayDownloaded = onPlayDownloaded)
            else -> {
                // Quality chips.
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "480p", "720p", "1080p", "4K").forEach { label ->
                        QualityChip(label = label, selected = filter == label, onClick = { filter = label })
                    }
                }
                when {
                    loading -> {
                        Column { repeat(3) { SkeletonRow() } }
                    }
                    filtered.isEmpty() && otherFiltered.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(NsDimens.CornerCard))
                                .background(NsColors.Surface)
                                .border(1.dp, NsColors.Divider, RoundedCornerShape(NsDimens.CornerCard))
                                .padding(horizontal = 22.dp, vertical = 38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (allVideos.isEmpty()) "No videos yet" else "Is quality me koi video nahi",
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = NsColors.TextPrimary
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (allVideos.isEmpty()) "Add a group or channel to see its videos here."
                                    else "Smart Filter har episode ki sirf best copy dikhata hai.",
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = NsColors.TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                if (allVideos.isNotEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    PrimaryButton(text = "Saare videos dikhao", onClick = { filter = "All" })
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(filtered, key = { it.chatId to it.messageId }) { video ->
                                EpisodeCard(
                                    repo = vm.repository,
                                    item = video,
                                    group = groupById[video.chatId],
                                    onClick = { playBest(video) },
                                    onLongClick = { actionItem = video },
                                    onCopiesClick = if (video.hiddenCopies > 0) {
                                        { copiesSheetFor = groupFor(video) }
                                    } else null
                                )
                            }
                            if (otherFiltered.isNotEmpty()) {
                                item {
                                    Text(
                                        "Other videos (${otherFiltered.size})",
                                        fontFamily = ManropeFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = NsColors.TextPrimary,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )
                                }
                                items(otherFiltered, key = { it.chatId to it.messageId }) { video ->
                                    EpisodeCard(
                                        repo = vm.repository,
                                        item = video,
                                        group = groupById[video.chatId],
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
        }
    }

    if (showAddSheet) {
        AddGroupSheet(vm = vm, onDismiss = { showAddSheet = false })
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
    actionItem?.let { item ->
        VideoActionSheet(
            vm = vm,
            item = item,
            onPlay = { playBest(item); actionItem = null },
            onPlayFromStart = {
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
            onWatchLaterToggled = {}
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

@Composable
private fun WatchLaterSection(
    vm: AppViewModel,
    onPlayVideo: (VideoItem, List<VideoItem>, Long) -> Unit
) {
    val items by vm.watchLater.collectAsState()
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NsDimens.CornerCard))
                .background(NsColors.Surface)
                .border(1.dp, NsColors.Divider, RoundedCornerShape(NsDimens.CornerCard))
                .padding(horizontal = 22.dp, vertical = 38.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No videos saved.", fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = NsColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Save videos from your groups to watch later.",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = NsColors.TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(items, key = { it.chatId to it.messageId }) { video ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VideoCard(
                        repo = vm.repository,
                        item = video,
                        onClick = { onPlayVideo(video, items, video.chatId) },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.toggleWatchLater(video) }) {
                        Text(
                            "Remove",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = NsColors.AccentBright
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsSection(
    vm: AppViewModel,
    onPlayDownloaded: (DownloadEntity) -> Unit
) {
    val downloads by vm.downloads.collectAsState()
    var tab by remember { mutableStateOf("All") }
    val filtered = remember(downloads, tab) {
        when (tab) {
            "Downloading" -> downloads.filter { it.status == "DOWNLOADING" || it.status == "QUEUED" || it.status == "PAUSED" }
            "Completed" -> downloads.filter { it.status == "COMPLETED" }
            "Failed" -> downloads.filter { it.status == "FAILED" }
            else -> downloads
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Downloading", "Completed", "Failed").forEach { label ->
                QualityChip(label = label, selected = tab == label, onClick = { tab = label })
            }
        }
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NsDimens.CornerCard))
                    .background(NsColors.Surface)
                    .border(1.dp, NsColors.Divider, RoundedCornerShape(NsDimens.CornerCard))
                    .padding(horizontal = 22.dp, vertical = 38.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No downloads here", fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = NsColors.TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Download videos from any video's action sheet to watch them offline.",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = NsColors.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(filtered, key = { it.chatId to it.messageId }) { dl ->
                    LibraryDownloadRow(vm = vm, dl = dl, onPlay = { onPlayDownloaded(dl) })
                }
            }
        }
    }
}

@Composable
private fun LibraryDownloadRow(vm: AppViewModel, dl: DownloadEntity, onPlay: () -> Unit) {
    val progress = if (dl.sizeBytes > 0) (dl.downloadedBytes.toFloat() / dl.sizeBytes.toFloat()).coerceIn(0f, 1f) else 0f
    fun toVideo() = VideoItem(
        dl.messageId, dl.chatId, dl.fileId, dl.remoteFileId, dl.title, "",
        VideoQuality.fromLabel(dl.qualityLabel), dl.durationSec, dl.sizeBytes, dl.dateEpochSec, null, dl.thumbLocalPath
    )
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(dl.title, fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = NsColors.TextPrimary, maxLines = 2)
                Text(
                    "${dl.qualityLabel} · ${FormatUtils.formatSize(dl.sizeBytes)} · ${dl.status.lowercase().replaceFirstChar { it.uppercase() }}",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = NsColors.TextSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            when (dl.status) {
                "COMPLETED" -> {
                    IconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
                        Icon(NsIcons.Play, contentDescription = "Play downloaded video", tint = NsColors.Primary, modifier = Modifier.size(20.dp))
                    }
                    TextButton(onClick = { vm.removeDownload(toVideo()) }) {
                        Text("Remove", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NsColors.AccentBright)
                    }
                }
                "FAILED" -> {
                    Button(onClick = { vm.resumeDownload(toVideo()) }) {
                        Text("Retry", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                "DOWNLOADING", "QUEUED" -> {
                    TextButton(onClick = { vm.pauseDownload(toVideo()) }) {
                        Text("Pause", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NsColors.AccentBright)
                    }
                }
                "PAUSED" -> {
                    Button(onClick = { vm.resumeDownload(toVideo()) }) {
                        Text("Resume", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
        if (dl.status == "DOWNLOADING" || dl.status == "QUEUED" || dl.status == "PAUSED") {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = NsColors.Primary,
                trackColor = NsColors.Outline
            )
        }
    }
}
