package com.nobi.nobistream2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobi.nobistream2.core.FormatUtils
import com.nobi.nobistream2.core.GroupItem
import com.nobi.nobistream2.core.SmartFilter
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.components.AddGroupSheet
import com.nobi.nobistream2.ui.components.AppTopBar
import com.nobi.nobistream2.ui.components.ConfirmDialog
import com.nobi.nobistream2.ui.components.EmptyState
import com.nobi.nobistream2.ui.components.GroupCard
import com.nobi.nobistream2.ui.components.MiniVideoCard
import com.nobi.nobistream2.ui.components.NsTopBar
import com.nobi.nobistream2.ui.components.PrimaryButton
import com.nobi.nobistream2.ui.components.SectionHeader
import com.nobi.nobistream2.ui.components.SkeletonRow
import com.nobi.nobistream2.ui.components.VideoThumb
import com.nobi.nobistream2.ui.theme.ManropeFamily
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens
import com.nobi.nobistream2.ui.theme.NsIcons
import com.nobi.nobistream2.ui.theme.SoraFamily
import androidx.compose.material3.Icon
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight

/**
 * Home (HTML redesign v1.1.0): paste chip, Hero Spotlight with the REAL
 * Continue Watching item, Your Groups, Recently Added 2-column rail,
 * HTML empty state with the 3 Hinglish steps when no groups exist.
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
    onSeeAllContinue: () -> Unit,
    onSeeAllLibrary: () -> Unit = {}
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

    // v1.1.1 item 11 (NobiStream 1 v2.6.1): one episode watched in two
    // qualities must not appear twice in the hero / Continue row.
    val continueDeduped = remember(continueWatching) { SmartFilter.dedupContinue(continueWatching) }
    val hero = continueDeduped.firstOrNull()
    val heroGroup = hero?.let { v -> groups.firstOrNull { it.chatId == v.chatId } }

    Box(modifier = Modifier.fillMaxSize().background(NsColors.Background)) {
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { vm.refreshGroups() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = NsDimens.ContentPadding,
                    end = NsDimens.ContentPadding,
                    top = NsDimens.ScreenTopPadding,
                    bottom = 96.dp
                )
            ) {
                item {
                    NsTopBar(
                        onAdd = { showAddSheet = true },
                        onRefresh = { vm.refreshGroups() }
                    )
                }
                if (groups.isEmpty() && !loading) {
                    item { HomeEmptyState(onAdd = { showAddSheet = true }) }
                } else {
                    // Paste chip.
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(NsColors.Primary.copy(alpha = 0.09f))
                                .border(1.dp, NsColors.AccentBright.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .clickable { showAddSheet = true }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(NsIcons.Link, contentDescription = null, tint = NsColors.AccentBright, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Paste a Telegram link",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = NsColors.AccentBright
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    // Hero Spotlight — real Continue Watching item.
                    if (hero != null) {
                        item {
                            HeroSpotlight(
                                vm = vm,
                                video = hero,
                                groupTitle = heroGroup?.title,
                                onResume = { onPlayVideo(hero, continueDeduped, hero.chatId, true) }
                            )
                        }
                    }
                    if (loading && groups.isEmpty()) {
                        items(3) { SkeletonRow() }
                    }
                    item {
                        SectionHeader(
                            title = "Your Groups",
                            count = "${groups.size} ${if (groups.size == 1) "group" else "groups"}"
                        )
                    }
                    items(groups, key = { it.chatId }) { group ->
                        GroupCard(
                            group = group,
                            onClick = { onOpenGroup(group.chatId) },
                            onLongClick = { onOpenGroupDetails(group.chatId) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (recentlyAdded.isNotEmpty() || recentLoading) {
                        item {
                            SectionHeader(title = "Recently Added") {
                                TextButton(onClick = onSeeAllLibrary) {
                                    Text(
                                        "See all",
                                        fontFamily = ManropeFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = NsColors.AccentBright
                                    )
                                }
                            }
                        }
                        if (recentLoading) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    repeat(2) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(NsColors.Skeleton)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(recentlyAdded.chunked(2)) { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    row.forEach { video ->
                                        MiniVideoCard(
                                            repo = vm.repository,
                                            item = video,
                                            onClick = { onPlayVideo(video, recentlyAdded, video.chatId, false) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                if (error != null) {
                    item {
                        Text(
                            error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NsColors.Error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
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

/** HTML Hero Spotlight: 16/10.5 image, gradient shade, tag, progress, Resume. */
@Composable
private fun HeroSpotlight(
    vm: AppViewModel,
    video: VideoItem,
    groupTitle: String?,
    onResume: () -> Unit
) {
    val remainingSec = (video.durationSec - video.progressSec.toInt()).coerceAtLeast(0)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NsColors.Surface)
            .clickable(onClick = onResume)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10.5f)) {
            VideoThumb(vm.repository, video, modifier = Modifier.fillMaxSize(), showBadges = false)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x0F060B14),
                                Color(0xE0060B14),
                                Color(0xF7060B14)
                            )
                        )
                    )
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp, top = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x8C060B14))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(NsColors.AccentBright))
                Spacer(Modifier.width(6.dp))
                Text(
                    "CONTINUE WATCHING",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.5.sp,
                    color = Color.White,
                    letterSpacing = 0.14.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                video.title,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (groupTitle != null) {
                Text(
                    groupTitle,
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = NsColors.TextOnHero,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { video.progressFraction },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
                color = NsColors.AccentBright,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .height(46.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(NsColors.Primary)
                        .clickable(onClick = onResume)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(NsIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Resume", fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(androidx.compose.ui.text.SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                            append(FormatUtils.formatDuration(remainingSec))
                        }
                        append(" left · ${video.quality.label}")
                    },
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = NsColors.TextOnHero
                )
            }
        }
    }
}

/** HTML empty state: circle art, message, full-width button, 3 Hinglish steps (exact HTML text). */
@Composable
private fun HomeEmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 64.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyState(
            icon = NsIcons.PlayInRect,
            title = "No groups added yet",
            subtitle = "Add a Telegram group or channel and all its videos will appear here — ready to stream."
        )
        PrimaryButton(text = "Add Group / Channel", onClick = onAdd, modifier = Modifier.fillMaxWidth())
        Column(
            modifier = Modifier.padding(top = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(
                "Telegram me group/channel kholo aur uska link copy karo",
                "Yahan paste karo — videos apne aap list me aa jayenge",
                "Tap karke stream karo, jahan chhoda wahi se resume hoga"
            ).forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(NsColors.Primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${i + 1}",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = NsColors.AccentBright
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Text(
                        step,
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = NsColors.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
    Column(modifier = Modifier.fillMaxSize().background(NsColors.Background)) {
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
        group?.let { g ->
            ConfirmDialog(
                title = "Remove this group?",
                body = "Remove ${g.title} from NobiStream 2? Its videos stay on Telegram.",
                confirmText = "Remove",
                destructive = true,
                onConfirm = { vm.removeGroup(g); showRemove = false; onRemove() },
                onDismiss = { showRemove = false }
            )
        }
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
    // v1.1.1 item 11: same dedup as the hero — one episode, one row.
    val dedupedItems = remember(items) { SmartFilter.dedupContinue(items) }
    Column(modifier = Modifier.fillMaxSize().background(NsColors.Background)) {
        AppTopBar(title = "Continue Watching", onBack = onBack)
        if (dedupedItems.isEmpty()) {
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
                items(dedupedItems, key = { it.chatId to it.messageId }) { video ->
                    com.nobi.nobistream2.ui.components.VideoCard(
                        repo = vm.repository,
                        item = video,
                        onClick = { onPlayVideo(video, dedupedItems, video.chatId, true) }
                    )
                }
            }
        }
    }
}
