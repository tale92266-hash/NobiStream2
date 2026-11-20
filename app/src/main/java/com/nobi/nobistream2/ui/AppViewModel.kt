package com.nobi.nobistream2.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobi.nobistream2.core.AuthState
import com.nobi.nobistream2.core.GroupItem
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.data.LibraryStore
import com.nobi.nobistream2.data.NobiRepository
import com.nobi.nobistream2.data.db.DownloadEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One shared ViewModel for the whole app (single-activity Compose). */
class AppViewModel(
    val repository: NobiRepository,
    val library: LibraryStore
) : ViewModel() {

    val authState: StateFlow<AuthState> = repository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.UNKNOWN)
    val currentUser = repository.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val groups: StateFlow<List<GroupItem>> = repository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val continueWatching: StateFlow<List<VideoItem>> = repository.observeContinueWatching()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val watchLater: StateFlow<List<VideoItem>> = library.observeWatchLater()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads: StateFlow<List<DownloadEntity>> = library.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _groupsLoading = MutableStateFlow(false)
    val groupsLoading: StateFlow<Boolean> = _groupsLoading
    private val _groupsError = MutableStateFlow<String?>(null)
    val groupsError: StateFlow<String?> = _groupsError
    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar
    private val _undoAction = MutableStateFlow<(() -> Unit)?>(null)
    val undoAction: StateFlow<(() -> Unit)?> = _undoAction

    fun showSnackbar(message: String, undo: (() -> Unit)? = null) {
        _snackbar.value = message
        _undoAction.value = undo
    }

    fun consumeSnackbar() {
        _snackbar.value = null
        _undoAction.value = null
    }

    suspend fun ensureStarted(): Boolean = repository.ensureStarted()

    fun refreshGroups() {
        viewModelScope.launch {
            _groupsLoading.value = true
            _groupsError.value = null
            val result = runCatching { repository.refreshGroups(groups.value) }.getOrNull()
            _groupsLoading.value = false
            if (result == null) {
                _groupsError.value = "Couldn't refresh groups. Check your connection and try again."
            } else if (result.failed > 0) {
                showSnackbar("Some groups couldn't refresh")
            }
        }
    }

    /** Add a group; returns the group or null with [onError] message. Never shows green success before TDLib confirms. */
    fun addGroup(link: String, onAdded: (GroupItem) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val clean = link.trim()
            if (groups.value.any { it.username.equals(clean.trimStart('@'), ignoreCase = true) }) {
                onError("This group is already added.")
                return@launch
            }
            runCatching { repository.addGroup(clean) }
                .onSuccess { onAdded(it) }
                .onFailure { onError("Couldn't add this group. Check the link and your connection, then try again.") }
        }
    }

    fun removeGroup(group: GroupItem) {
        viewModelScope.launch {
            repository.removeGroup(group.chatId)
            showSnackbar("Group removed")
        }
    }

    fun toggleWatchLater(item: VideoItem) {
        viewModelScope.launch {
            val added = library.toggleWatchLater(item)
            if (added) {
                showSnackbar("Added to Watch Later") { toggleWatchLater(item) }
            } else {
                showSnackbar("Removed from Watch Later")
            }
        }
    }

    suspend fun isWatchLater(item: VideoItem): Boolean = library.isWatchLater(item)

    fun startDownload(item: VideoItem) {
        viewModelScope.launch {
            showSnackbar("Download started")
            library.downloadVideo(item, isPaused = { downloadPaused[item.chatId to item.messageId] == true }) { _, _ -> }
        }
    }

    private val downloadPaused = mutableMapOf<Pair<Long, Long>, Boolean>()

    fun pauseDownload(item: VideoItem) {
        downloadPaused[item.chatId to item.messageId] = true
        showSnackbar("Download paused")
    }

    fun resumeDownload(item: VideoItem) {
        downloadPaused[item.chatId to item.messageId] = false
        startDownload(item)
    }

    fun removeDownload(item: VideoItem) {
        viewModelScope.launch { library.removeDownload(item) }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            library.clearCompletedDownloads()
            showSnackbar("Downloads cleared")
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.logout() }
            onDone()
        }
    }
}
