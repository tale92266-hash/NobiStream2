package com.nobi.nobistream2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nobi.nobistream2.core.AuthState
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.data.LibraryStore
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.screens.ChannelScreen
import com.nobi.nobistream2.ui.screens.ContinueAllScreen
import com.nobi.nobistream2.ui.screens.DownloadsScreen
import com.nobi.nobistream2.ui.screens.GroupDetailsScreen
import com.nobi.nobistream2.ui.screens.HomeScreen
import com.nobi.nobistream2.ui.screens.LoginScreen
import com.nobi.nobistream2.ui.screens.MiniPlayer
import com.nobi.nobistream2.ui.screens.PlayerScreen
import com.nobi.nobistream2.ui.screens.SearchScreen
import com.nobi.nobistream2.ui.screens.SettingsScreen
import com.nobi.nobistream2.ui.screens.WatchLaterScreen
import com.nobi.nobistream2.ui.theme.NobiStream2Theme
import com.nobi.nobistream2.ui.theme.NsColors

/**
 * Single-activity Compose app. Bottom nav: Home / Watch Later / Downloads
 * (Appendix A3). The player is a full-screen destination; the mini-player
 * sits above the bottom nav while something plays.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NobiStream2Application
        val locator = app.serviceLocator
        val library = LibraryStore(locator.database, locator.gateway)
        app.playerSession.attachRepository(locator.repository, locator.gateway)
        setContent {
            NobiStream2Theme {
                NobiStream2App(
                    vm = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                AppViewModel(locator.repository, library) as T
                        }
                    ),
                    playerSession = app.playerSession
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Save the resume position when the app backgrounds mid-video.
        (application as NobiStream2Application).playerSession.stopAndSave()
    }
}

@Composable
fun NobiStream2App(vm: AppViewModel, playerSession: com.nobi.nobistream2.ui.player.PlayerSession) {
    val navController = rememberNavController()
    val authState by vm.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by vm.snackbar.collectAsState()
    val undoAction by vm.undoAction.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isPlayer = currentRoute?.startsWith("player") == true
    val showBottomNav = !isPlayer && authState == AuthState.READY &&
        (currentRoute == "home" || currentRoute == "watchlater" || currentRoute == "downloads")
    val isPlaying by playerSession.current.collectAsState()

    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message, actionLabel = if (undoAction != null) "Undo" else null)
        if (result == SnackbarResult.ActionPerformed) undoAction?.invoke()
        vm.consumeSnackbar()
    }

    // Route the user: not signed in -> login, signed in -> home.
    LaunchedEffect(authState) {
        if (authState == AuthState.READY && currentRoute == "login") {
            navController.navigate("home") { popUpTo("login") { inclusive = true } }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = NsColors.Background,
        bottomBar = {
            if (showBottomNav) {
                Column {
                    if (isPlaying != null) {
                        MiniPlayer(session = playerSession, onOpenPlayer = { navController.navigate("player") })
                    }
                    NavigationBar(containerColor = NsColors.Surface) {
                        NavigationBarItem(
                            selected = currentRoute == "home",
                            onClick = { navController.navigate("home") { launchSingleTop = true } },
                            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == "watchlater",
                            onClick = { navController.navigate("watchlater") { launchSingleTop = true } },
                            icon = { Icon(Icons.Filled.Bookmark, contentDescription = "Watch Later") },
                            label = { Text("Watch Later") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == "downloads",
                            onClick = { navController.navigate("downloads") { launchSingleTop = true } },
                            icon = { Icon(Icons.Filled.Download, contentDescription = "Downloads") },
                            label = { Text("Downloads") }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(navController = navController, startDestination = "login") {
                composable("login") {
                    LoginScreen(
                        vm = vm,
                        onSignedIn = { navController.navigate("home") { popUpTo("login") { inclusive = true } } },
                        onOpenSettings = { navController.navigate("settings") }
                    )
                }
                composable("home") {
                    HomeScreen(
                        vm = vm,
                        onOpenGroup = { id -> navController.navigate("channel/$id") },
                        onOpenGroupDetails = { id -> navController.navigate("group/$id") },
                        onOpenSearch = { navController.navigate("search") },
                        onOpenSettings = { navController.navigate("settings") },
                        onPlayVideo = { item, list, chatId, fromContinue ->
                            playerSession.open(list, list.indexOfFirst { it.messageId == item.messageId }.coerceAtLeast(0), chatId, fromContinue)
                            navController.navigate("player")
                        },
                        onSeeAllContinue = { navController.navigate("continue") }
                    )
                }
                composable(
                    route = "channel/{chatId}",
                    arguments = listOf(navArgument("chatId") { type = NavType.LongType })
                ) { entry ->
                    val chatId = entry.arguments?.getLong("chatId") ?: 0L
                    ChannelScreen(
                        vm = vm,
                        chatId = chatId,
                        onBack = { navController.popBackStack() },
                        onPlayVideo = { item, list, id ->
                            playerSession.open(list, list.indexOfFirst { it.messageId == item.messageId }.coerceAtLeast(0), id, false)
                            navController.navigate("player")
                        },
                        onOpenSearch = { navController.navigate("search") }
                    )
                }
                composable(
                    route = "group/{chatId}",
                    arguments = listOf(navArgument("chatId") { type = NavType.LongType })
                ) { entry ->
                    val chatId = entry.arguments?.getLong("chatId") ?: 0L
                    GroupDetailsScreen(
                        vm = vm,
                        chatId = chatId,
                        onBack = { navController.popBackStack() },
                        onOpenGroup = { id -> navController.navigate("channel/$id") },
                        onRemove = { navController.popBackStack() }
                    )
                }
                composable("continue") {
                    ContinueAllScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onPlayVideo = { item, list, chatId, fromContinue ->
                            playerSession.open(list, list.indexOfFirst { it.messageId == item.messageId }.coerceAtLeast(0), chatId, fromContinue)
                            navController.navigate("player")
                        }
                    )
                }
                composable("search") {
                    SearchScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onPlayVideo = { item, list, chatId ->
                            playerSession.open(list, list.indexOfFirst { it.messageId == item.messageId }.coerceAtLeast(0), chatId, false)
                            navController.navigate("player")
                        }
                    )
                }
                composable("watchlater") {
                    WatchLaterScreen(
                        vm = vm,
                        onPlayVideo = { item, list, chatId ->
                            playerSession.open(list, list.indexOfFirst { it.messageId == item.messageId }.coerceAtLeast(0), chatId, false)
                            navController.navigate("player")
                        },
                        onBrowseLibrary = { navController.navigate("home") }
                    )
                }
                composable("downloads") {
                    DownloadsScreen(
                        vm = vm,
                        onPlayDownloaded = { dl ->
                            val path = dl.localPath
                            if (path != null) {
                                val item = VideoItem(
                                    messageId = dl.messageId, chatId = dl.chatId, fileId = dl.fileId,
                                    remoteFileId = dl.remoteFileId, title = dl.title, caption = "",
                                    quality = com.nobi.nobistream2.core.VideoQuality.fromLabel(dl.qualityLabel),
                                    durationSec = dl.durationSec, sizeBytes = dl.sizeBytes,
                                    dateEpochSec = dl.dateEpochSec, thumbFileId = null, thumbLocalPath = dl.thumbLocalPath
                                )
                                playerSession.openDownloaded(item, path)
                                navController.navigate("player")
                            }
                        }
                    )
                }
                composable("settings") {
                    SettingsScreen(vm = vm, onBack = { navController.popBackStack() })
                }
                composable("player") {
                    PlayerScreen(
                        session = playerSession,
                        onBack = { navController.popBackStack() },
                        onOpenVideo = { _, _, _ -> }
                    )
                }
            }
        }
    }
}
