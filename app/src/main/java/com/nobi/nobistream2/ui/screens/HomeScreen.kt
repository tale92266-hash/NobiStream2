package com.nobi.nobistream2.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import com.nobi.nobistream2.core.FormatUtils
import com.nobi.nobistream2.core.GroupItem
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.components.AppTopBar
import com.nobi.nobistream2.ui.components.ConfirmDialog
import com.nobi.nobistream2.ui.components.EmptyState
import com.nobi.nobistream2.ui.components.GroupCard
import com.nobi.nobistream2.ui.components.PrimaryButton
import com.nobi.nobistream2.ui.components.SectionHeader
import com.nobi.nobistream2.ui.components.SkeletonRow
import com.nobi.nobistream2.ui.components.VideoThumb
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens

/**
 * Home (Appendix A3): one vertically scrolling lazy list — no nested
 * vertical scroll containers. Continue Watching (240dp cards) +
 * Recently Added carousel + My Groups + FAB above the bottom nav.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenGroup: (Long) -> Unit,
    onOpenGroupDetails: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>, Long, Boolean) -> Unit,
    onSeeAllContinue: () -> Unit
) {
    val groups by vm.groups.collectAsState()
    val continueWatching by vm.continueWatching.collectAsState()
    val loading by vm.groupsLoading.collectAsState()
    val error by vm.groupsError.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var groupToRemove by remember { mutableStateOf<GroupItem?>(null) }
    var recentlyAdded by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var recentLoading by remember { mutableStateOf(false) }

    LaunchedEffect(groups) {
        if (groups.isNotEmpty() && recentlyAdded.isEmpty()) {
            recentLoading = true
            val page = runCatching { vm.repository.loadLibraryPage(groups, emptyMap(), 20) }.getOrNull()
            recentlyAdded = page?.videos?.take(10) ?: emptyList()
            recentLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { vm.refreshGroups() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item {
                    AppTopBar(
                        title = "NobiStream 2",
                        actions = {
                            IconButton(onClick = onOpenSearch, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.Search, contentDescription = "Search videos", tint = NsColors.TextPrimary)
                            }
                            IconButton(onClick = { vm.refreshGroups() }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh groups", tint = NsColors.TextPrimary)
                            }
                            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = NsColors.TextPrimary)
                            }
                        }
                    )
                }
                if (loading && groups.isEmpty()) {
                    items(3) { SkeletonRow(modifier = Modifier.padding(horizontal = NsDimens.ContentPadding)) }
                }
                if (continueWatching.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Continue Watching") {
                            TextButton(onClick = onSeeAllContinue) { Text("See all") }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = NsDimens.ContentPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(continueWatching, key = { it.chatId to it.messageId }) { video ->
                                Column(
                                    modifier = Modifier.width(NsDimens.VideoCardWidth)
                                        .clickable { onPlayVideo(video, continueWatching, video.chatId, true) }
                                ) {
                                    VideoThumb(vm.repository, video, modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(6.dp))
                                    Text(video.title, style = MaterialTheme.typography.titleSmall, color = NsColors.TextPrimary, maxLines = 2)
                                    Text(
                                        "${FormatUtils.formatResumePosition(video.progressSec, video.durationSec)} left",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NsColors.TextSecondary
                                    )
                                    LinearProgressIndicator(
                                        progress = { video.progressFraction },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                        color = NsColors.Primary,
                                        trackColor = NsColors.Outline
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(NsDimens.SectionSpacing))
                    }
                }
                if (recentlyAdded.isNotEmpty() || recentLoading) {
                    item {
                        SectionHeader(title = "Recently Added")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = NsDimens.ContentPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (recentLoading) {
                                items(3) {
                                    Box(modifier = Modifier.width(NsDimens.RecentCardWidth).height(140.dp).clip(RoundedCornerShape(16.dp)).background(NsColors.Skeleton))
                                }
                            } else {
                                items(recentlyAdded, key = { it.chatId to it.messageId }) { video ->
                                    Column(
                                        modifier = Modifier.width(NsDimens.RecentCardWidth)
                                            .clickable { onPlayVideo(video, recentlyAdded, video.chatId, false) }
                                    ) {
                                        VideoThumb(vm.repository, video, modifier = Modifier.fillMaxWidth())
                                        Spacer(Modifier.height(6.dp))
                                        Text(video.title, style = MaterialTheme.typography.titleSmall, color = NsColors.TextPrimary, maxLines = 2)
                                        Text(video.quality.label, style = MaterialTheme.typography.bodySmall, color = NsColors.TextSecondary)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(NsDimens.SectionSpacing))
                    }
                }
                item { SectionHeader(title = "My Groups") }
                if (groups.isEmpty() && !loading) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Group,
                            title = "No groups added yet",
                            subtitle = "Paste a Telegram group or channel link to see all its videos in one place.",
                            buttonText = "Add Group / Channel",
                            onButtonClick = { showAddSheet = true }
                        )
                    }
                } else {
                    items(groups, key = { it.chatId }) { group ->
                        Box(modifier = Modifier.padding(horizontal = NsDimens.ContentPadding)) {
                            GroupCard(
                                group = group,
                                onClick = { onOpenGroup(group.chatId) },
                                onLongClick = { onOpenGroupDetails(group.chatId) }
                            )
                        }
                    }
                }
                if (error != null) {
                    item {
                        Text(
                            error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NsColors.Error,
                            modifier = Modifier.padding(NsDimens.ContentPadding)
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp),
            containerColor = NsColors.Primary,
            contentColor = androidx.compose.ui.graphics.Color.White
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Group / Channel")
        }
    }

    if (showAddSheet) {
        AddGroupSheet(vm = vm, onDismiss = { showAddSheet = false })
    }
    groupToRemove?.let { group ->
        ConfirmDialog(
            title = "Remove this group?",
            body = "Remove ${group.title} from NobiStream 2? Its videos stay on Telegram.",
            confirmText = "Remove",
            destructive = true,
            onConfirm = { vm.removeGroup(group); groupToRemove = null },
            onDismiss = { groupToRemove = null }
        )
    }
}

/** Appendix A4: Add Group bottom sheet — validate immediately, no green success before TDLib confirms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var link by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val valid = remember(link) {
        val t = link.trim()
        t.startsWith("https://t.me/") || t.startsWith("t.me/") || t.startsWith("@") || t.matches(Regex("[A-Za-z0-9_]{5,}"))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Add Group / Channel", style = MaterialTheme.typography.titleLarge, color = NsColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Public groups and channels you can already read. Private links need an invite you have accepted.",
                style = MaterialTheme.typography.bodyMedium,
                color = NsColors.TextSecondary
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = link,
                onValueChange = {
                    link = it
                    status = when {
                        it.isBlank() -> null
                        valid -> "Link looks valid — tap Add to confirm with Telegram."
                        else -> "That doesn't look like a Telegram link. Paste a t.me link or a @username."
                    }
                },
                label = { Text("Telegram link") },
                placeholder = { Text("https://t.me/channel_name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().height(NsDimens.InputHeight)
            )
            if (status != null) {
                Spacer(Modifier.height(8.dp))
                Text(status!!, style = MaterialTheme.typography.bodyMedium, color = if (valid) NsColors.Success else NsColors.Error)
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = "Add",
                onClick = {
                    busy = true
                    status = null
                    vm.addGroup(
                        link,
                        onAdded = { onDismiss() },
                        onError = { message -> status = message; busy = false }
                    )
                },
                enabled = valid && !busy,
                loading = busy,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Appendix A5: Group Details — video count, last refreshed, sync status, source link, quality distribution (only if accurate). */
@Composable
fun GroupDetailsScreen(
    vm: AppViewModel,
    chatId: Long,
    onBack: () -> Unit,
    onOpenGroup: (Long) -> Unit,
    onRemove: () -> Unit
) {
    val groups by vm.groups.collectAsState()
    val group = groups.firstOrNull { it.chatId == chatId }
    var showRemove by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    LaunchedEffect(chatId) {
        videos = runCatching { vm.repository.loadVideos(chatId, 0L, 50) }.getOrNull()?.videos ?: emptyList()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = group?.title ?: "Group", subtitle = group?.let { "${it.videoCount} videos" }, onBack = onBack)
        if (group == null) {
            EmptyState(icon = Icons.Filled.Group, title = "Group not found", subtitle = "This group may have been removed.")
            return@Column
        }
        Column(modifier = Modifier.padding(horizontal = NsDimens.ContentPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Source: ", style = MaterialTheme.typography.bodyMedium, color = NsColors.TextSecondary)
                Text("@${group.username}", style = MaterialTheme.typography.bodyMedium, color = NsColors.TextPrimary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Last refreshed: ${FormatUtils.formatDate(group.lastUpdatedEpochSec)}",
                style = MaterialTheme.typography.bodyMedium,
                color = NsColors.TextSecondary
            )
            Spacer(Modifier.height(20.dp))
            Text("Quality", style = MaterialTheme.typography.titleMedium, color = NsColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            val counts = videos.groupBy { it.quality.label }.mapValues { it.value.size }
            if (videos.isEmpty()) {
                Text("No videos loaded yet — open the group to load them.", style = MaterialTheme.typography.bodyMedium, color = NsColors.TextSecondary)
            } else {
                counts.entries.sortedByDescending { it.value }.forEach { (label, count) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = NsColors.TextPrimary)
                        Text("$count", style = MaterialTheme.typography.bodyMedium, color = NsColors.TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = "Open group", onClick = { onOpenGroup(chatId) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { showRemove = true }) {
                Text("Remove group", color = NsColors.Error)
            }
        }
    }
    if (showRemove) {
        ConfirmDialog(
            title = "Remove this group?",
            body = "Remove ${group.title} from NobiStream 2? Its videos stay on Telegram.",
            confirmText = "Remove",
            destructive = true,
            onConfirm = { vm.removeGroup(group); showRemove = false; onRemove() },
            onDismiss = { showRemove = false }
        )
    }
}

/** Full Continue Watching list (no 10-item cap) — the Home "See all" target. */
@Composable
fun ContinueAllScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>, Long, Boolean) -> Unit
) {
    val items by vm.repository.observeContinueWatchingAll().collectAsState(initial = emptyList())
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "Continue Watching", onBack = onBack)
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Bookmark,
                title = "Nothing to continue",
                subtitle = "Videos you start watching will show up here so you can resume them."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = NsDimens.ContentPadding, vertical = 8.dp)
            ) {
                items(items, key = { it.chatId to it.messageId }) { video ->
                    com.nobi.nobistream2.ui.components.VideoCard(
                        repo = vm.repository,
                        item = video,
                        onClick = { onPlayVideo(video, items, video.chatId, true) }
                    )
                }
            }
        }
    }
}
