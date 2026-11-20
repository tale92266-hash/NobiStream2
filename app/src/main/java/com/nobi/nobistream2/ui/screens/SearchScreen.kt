package com.nobi.nobistream2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobi.nobistream2.core.FormatUtils
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.components.AddGroupSheet
import com.nobi.nobistream2.ui.components.NsTopBar
import com.nobi.nobistream2.ui.components.QualityChip
import com.nobi.nobistream2.ui.components.SkeletonRow
import com.nobi.nobistream2.ui.components.VideoThumb
import com.nobi.nobistream2.ui.theme.ManropeFamily
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens
import com.nobi.nobistream2.ui.theme.NsIcons
import com.nobi.nobistream2.ui.theme.SoraFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search (HTML redesign v1.1.0): "Search" Sora 26 + Hinglish sub, 50dp
 * field, recent chips, "Results" + real count, 86dp result rows with
 * divider lines, "Kuch nahi mila" empty box. Searches the real library
 * (all groups, title/caption/season/episode), newest-first.
 */
@Composable
fun SearchScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>, Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    val recent by vm.repository.settingsStore.recentSearches.collectAsState(initial = emptyList())
    val groups by vm.groups.collectAsState()

    LaunchedEffect(query) {
        searchJob?.cancel()
        if (query.isBlank()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        searchJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            delay(300)
            results = runCatching { vm.repository.searchVideos(query, groups) }.getOrDefault(emptyList())
            searching = false
            if (query.isNotBlank()) vm.repository.settingsStore.addRecentSearch(query)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NsColors.Background)
            .padding(horizontal = NsDimens.ContentPadding)
    ) {
        NsTopBar(onAdd = { showAddSheet = true }, onRefresh = { vm.refreshGroups() })
        Text(
            "Search",
            fontFamily = SoraFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
            color = NsColors.TextPrimary,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Apne groups ke saare videos me dhundho",
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            color = NsColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        // Search field: surface bg, 1px line, radius 16, 50dp.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(NsColors.Surface)
                .border(1.dp, NsColors.Divider, RoundedCornerShape(16.dp))
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(NsIcons.Search, contentDescription = null, tint = NsColors.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.5.sp,
                    color = NsColors.TextPrimary
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            "Episodes, seasons, groups…",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.5.sp,
                            color = NsColors.TextDisabled
                        )
                    }
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                Icon(
                    NsIcons.Close,
                    contentDescription = "Clear search",
                    tint = NsColors.TextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { query = "" }
                )
            }
        }

        if (query.isBlank()) {
            if (recent.isNotEmpty()) {
                Text(
                    "Recent",
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = NsColors.TextPrimary,
                    modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
                )
                // Wrapping recent chips (manual rows of up to 3).
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.take(6).chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { q ->
                                QualityChip(label = q, selected = false, onClick = { query = q })
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            vm.repository.settingsStore.clearRecentSearches()
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        "Clear recent",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NsColors.AccentBright
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Results",
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = NsColors.TextPrimary
                )
                Text(
                    "${results.size} ${if (results.size == 1) "video" else "videos"}",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = NsColors.TextDisabled
                )
            }
            when {
                searching -> {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        repeat(3) { SkeletonRow() }
                    }
                }
                results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(NsDimens.CornerCard))
                            .background(NsColors.Surface)
                            .border(1.dp, NsColors.Divider, RoundedCornerShape(NsDimens.CornerCard))
                            .padding(horizontal = 22.dp, vertical = 38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Kuch nahi mila",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = NsColors.TextPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Is naam ka koi video tumhare groups me nahi hai. Spelling check karo ya dusra naam try karo.",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = NsColors.TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        items(results, key = { it.chatId to it.messageId }) { video ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clickable { onPlayVideo(video, results, video.chatId) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                VideoThumb(
                                    repo = vm.repository,
                                    item = video,
                                    showBadges = false,
                                    modifier = Modifier
                                        .width(86.dp)
                                        .aspectRatio(16f / 9f)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        video.title,
                                        fontFamily = ManropeFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.5.sp,
                                        color = NsColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        listOfNotNull(
                                            video.quality.label.takeIf { video.quality.label != "Unknown" },
                                            FormatUtils.formatDuration(video.durationSec).takeIf { video.durationSec > 0 },
                                            FormatUtils.formatSize(video.sizeBytes).takeIf { video.sizeBytes > 0 }
                                        ).joinToString(" · "),
                                        fontFamily = ManropeFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = NsColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NsColors.Divider))
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddGroupSheet(vm = vm, onDismiss = { showAddSheet = false })
    }
}
