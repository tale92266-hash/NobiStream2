package com.nobi.nobistream2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.nobi.nobistream2.ui.screens.LibraryScreen
import com.nobi.nobistream2.ui.screens.LoginScreen
import com.nobi.nobistream2.ui.screens.MiniPlayer
import com.nobi.nobistream2.ui.screens.PlayerScreen
import com.nobi.nobistream2.ui.screens.SearchScreen
import com.nobi.nobistream2.ui.screens.SettingsScreen
import com.nobi.nobistream2.ui.screens.WatchLaterScreen
import com.nobi.nobistream2.ui.theme.ManropeFamily
import com.nobi.nobistream2.ui.theme.NobiStream2Theme
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsIcons

/**
 * Single-activity Compose app. Bottom nav (HTML redesign v1.1.0):
 * Home / Search / Library / Settings. The player is a full-screen
 * destination (nav hidden); Watch Later + Downloads live INSIDE the
 * Library screen. The mini-player sits above the bottom nav while
 * something plays.
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
                    playerSession = app.playerSession,
                    repository = locator.repository
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
fun NobiStream2App(
    vm: AppViewModel,
    playerSession: com.nobi.nobistream2.ui.player.PlayerSession,
    repository: com.nobi.nobistream2.data.NobiRepository
) {
    val navController = rememberNavController()
    val authState by vm.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by vm.snackbar.collectAsState()
    val undoAction by vm.undoAction.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isPlayer = currentRoute?.startsWith("player") == true
    val showBottomNav = !isPlayer && authState == AuthState.READY &&
        (currentRoute == "home" || currentRoute == "search" || currentRoute == "library" || currentRoute == "settings")
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
                    NsBottomNav(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    )
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
                        onSeeAllContinue = { navController.navigate("continue") },
                        onSeeAllLibrary = { navController.navigate("library") }
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
                composable("library") {
                    LibraryScreen(
                        vm = vm,
                        onOpenGroup = { id -> navController.navigate("channel/$id") },
                        onPlayVideo = { item, list, chatId ->
                            playerSession.open(list, list.indexOfFirst { it.messageId == item.messageId }.coerceAtLeast(0), chatId, false)
                            navController.navigate("player")
                        },
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
                composable("watchlater") {
                    WatchLaterScreen(
                        vm = vm,
                        onPlayVideo = { item, list, chatId ->
                            playerSession.open(list, list.indexOfFirst { it.messageId == item.messageId }.coerceAtLeast(0), chatId, false)
                            navController.navigate("player")
                        },
                        onBrowseLibrary = { navController.navigate("library") }
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
                        repo = repository,
                        onBack = { navController.popBackStack() },
                        onOpenVideo = { _, _, _ -> }
                    )
                }
            }
        }
    }
}

/** HTML bottom nav: 4 tabs, active icon inside an accent pill, 11.5sp 700 labels. */
@Composable
fun NsBottomNav(currentRoute: String?, onNavigate: (String) -> Unit) {
    val tabs = listOf(
        Triple("home", "Home", NsIcons.Home),
        Triple("search", "Search", NsIcons.Search),
        Triple("library", "Library", NsIcons.Library),
        Triple("settings", "Settings", NsIcons.Settings)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NsColors.BottomNavBg)
            .border(1.dp, NsColors.Divider)
            .padding(top = 9.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEach { (route, label, icon) ->
            val selected = currentRoute == route
            Column(
                modifier = Modifier
                    .clickable { onNavigate(route) }
                    .padding(vertical = 7.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (selected) NsColors.Primary.copy(alpha = 0.22f) else Color.Transparent
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (selected) NsColors.AccentBright else NsColors.TextDisabled,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    label,
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = if (selected) NsColors.TextPrimary else NsColors.TextDisabled,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}
