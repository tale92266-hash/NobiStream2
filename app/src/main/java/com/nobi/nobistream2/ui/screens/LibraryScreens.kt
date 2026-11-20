package com.nobi.nobistream2.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nobi.nobistream2.core.FormatUtils
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.data.db.DownloadEntity
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.components.AppTopBar
import com.nobi.nobistream2.ui.components.EmptyState
import com.nobi.nobistream2.ui.components.QualityChip
import com.nobi.nobistream2.ui.components.VideoCard
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens

/**
 * Watch Later (Appendix A11): same VideoCard, swipe-removal replaced by
 * long-press-free explicit Remove + Snackbar Undo (safe, no gesture
 * conflicts). Removing here never removes from the source.
 */
@Composable
fun WatchLaterScreen(
    vm: AppViewModel,
    onPlayVideo: (VideoItem, List<VideoItem>, Long) -> Unit,
    onBrowseLibrary: () -> Unit
) {
    val items by vm.watchLater.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "Watch Later", subtitle = "${items.size} saved")
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Bookmark,
                title = "No videos saved.",
                subtitle = "Save videos from your groups to watch later.",
                buttonText = "Browse Library",
                onButtonClick = onBrowseLibrary
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = NsDimens.ContentPadding, vertical = 8.dp)
            ) {
                items(items, key = { it.chatId to it.messageId }) { video ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VideoCard(
                            repo = vm.repository,
                            item = video,
                            onClick = { onPlayVideo(video, items, video.chatId) },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            vm.toggleWatchLater(video)
                        }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

/**
 * Downloads (Appendix A12): All / Downloading / Completed / Failed
 * segmented filter, real TDLib state, determinate progress, Pause,
 * Completed = Play / Remove, Failed = Retry.
 */
@Composable
fun DownloadsScreen(
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
        AppTopBar(
            title = "Downloads",
            subtitle = "${downloads.count { it.status == "COMPLETED" }} completed",
            actions = {
                TextButton(onClick = { vm.clearCompletedDownloads() }) { Text("Clear finished") }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NsDimens.ContentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Downloading", "Completed", "Failed").forEach { label ->
                QualityChip(label = label, selected = tab == label, onClick = { tab = label })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Download,
                title = "No downloads here",
                subtitle = "Download videos from any video's action sheet to watch them offline."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = NsDimens.ContentPadding, vertical = 8.dp)
            ) {
                items(filtered, key = { it.chatId to it.messageId }) { dl ->
                    DownloadRow(vm = vm, dl = dl, onPlay = { onPlayDownloaded(dl) })
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(vm: AppViewModel, dl: DownloadEntity, onPlay: () -> Unit) {
    val progress = if (dl.sizeBytes > 0) (dl.downloadedBytes.toFloat() / dl.sizeBytes.toFloat()).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(dl.title, style = MaterialTheme.typography.titleSmall, color = NsColors.TextPrimary, maxLines = 2)
                Text(
                    "${dl.qualityLabel} · ${FormatUtils.formatSize(dl.sizeBytes)} · ${dl.status.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NsColors.TextSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            when (dl.status) {
                "COMPLETED" -> {
                    IconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play downloaded video", tint = NsColors.Primary)
                    }
                    TextButton(onClick = {
                        vm.removeDownload(VideoItem(dl.messageId, dl.chatId, dl.fileId, dl.remoteFileId, dl.title, "", com.nobi.nobistream2.core.VideoQuality.fromLabel(dl.qualityLabel), dl.durationSec, dl.sizeBytes, dl.dateEpochSec, null, dl.thumbLocalPath))
                    }) { Text("Remove") }
                }
                "FAILED" -> {
                    Button(onClick = {
                        vm.resumeDownload(VideoItem(dl.messageId, dl.chatId, dl.fileId, dl.remoteFileId, dl.title, "", com.nobi.nobistream2.core.VideoQuality.fromLabel(dl.qualityLabel), dl.durationSec, dl.sizeBytes, dl.dateEpochSec, null, dl.thumbLocalPath))
                    }) { Text("Retry") }
                }
                "DOWNLOADING", "QUEUED" -> {
                    TextButton(onClick = {
                        vm.pauseDownload(VideoItem(dl.messageId, dl.chatId, dl.fileId, dl.remoteFileId, dl.title, "", com.nobi.nobistream2.core.VideoQuality.fromLabel(dl.qualityLabel), dl.durationSec, dl.sizeBytes, dl.dateEpochSec, null, dl.thumbLocalPath))
                    }) { Text("Pause") }
                }
                "PAUSED" -> {
                    Button(onClick = {
                        vm.resumeDownload(VideoItem(dl.messageId, dl.chatId, dl.fileId, dl.remoteFileId, dl.title, "", com.nobi.nobistream2.core.VideoQuality.fromLabel(dl.qualityLabel), dl.durationSec, dl.sizeBytes, dl.dateEpochSec, null, dl.thumbLocalPath))
                    }) { Text("Resume") }
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
