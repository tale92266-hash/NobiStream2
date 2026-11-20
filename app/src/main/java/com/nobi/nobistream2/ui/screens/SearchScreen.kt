package com.nobi.nobistream2.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.components.AppTopBar
import com.nobi.nobistream2.ui.components.EmptyState
import com.nobi.nobistream2.ui.components.SkeletonRow
import com.nobi.nobistream2.ui.components.VideoCard
import com.nobi.nobistream2.ui.theme.NsDimens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search (Appendix A8): 56dp input, recent searches (48dp rows), results
 * use the same VideoCard as Channel. Typing debounces 300ms and searches
 * locally first — nothing is submitted to Telegram per letter; the
 * server search runs once per settled query via the repository.
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

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "Search", onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search videos in all groups") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = NsDimens.ContentPadding).height(NsDimens.InputHeight)
        )
        if (query.isBlank()) {
            if (recent.isNotEmpty()) {
                Text(
                    "Recent searches",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(NsDimens.ContentPadding)
                )
                recent.forEach { q ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = NsDimens.ContentPadding),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = com.nobi.nobistream2.ui.theme.NsColors.TextSecondary)
                        TextButton(onClick = { query = q }, modifier = Modifier.weight(1f)) {
                            Text(q, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                TextButton(
                    onClick = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            vm.repository.settingsStore.clearRecentSearches()
                        }
                    },
                    modifier = Modifier.padding(horizontal = NsDimens.ContentPadding)
                ) { Text("Clear") }
            } else {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Search all your videos",
                    subtitle = "Search titles and captions across every group you added."
                )
            }
        } else if (searching) {
            Column(modifier = Modifier.padding(horizontal = NsDimens.ContentPadding)) {
                repeat(3) { SkeletonRow() }
            }
        } else if (results.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Search,
                title = "No videos found",
                subtitle = "Try different words, or check the spelling."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = NsDimens.ContentPadding, vertical = 8.dp)
            ) {
                items(results, key = { it.chatId to it.messageId }) { video ->
                    VideoCard(
                        repo = vm.repository,
                        item = video,
                        onClick = { onPlayVideo(video, results, video.chatId) }
                    )
                }
            }
        }
    }
}
