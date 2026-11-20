package com.nobi.nobistream2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.components.AddGroupSheet
import com.nobi.nobistream2.ui.components.ConfirmDialog
import com.nobi.nobistream2.ui.components.NsOptionSheet
import com.nobi.nobistream2.ui.components.NsSwitch
import com.nobi.nobistream2.ui.components.NsTopBar
import com.nobi.nobistream2.ui.components.PrimaryButton
import com.nobi.nobistream2.ui.theme.ManropeFamily
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens
import com.nobi.nobistream2.ui.theme.NsIcons
import com.nobi.nobistream2.ui.theme.SoraFamily
import kotlinx.coroutines.launch

/**
 * Settings (HTML redesign v1.1.0): "Settings" Sora 26 + Hinglish sub,
 * grouped cards (Playback / Subtitles / About), 38dp icon tiles, HTML
 * 50x29 switches, option rows open bottom sheets (no dropdowns), live
 * subtitle preview. Every switch/value is wired to the REAL
 * SettingsStore keys. Telegram credentials + logout stay below About
 * (the app still needs them when no built-in credentials exist).
 */
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val repo = vm.repository
    val settings = repo.settingsStore
    val autoplay by settings.autoplay.collectAsState(initial = true)
    val smart by settings.smartFilter.collectAsState(initial = true)
    val openLast by settings.openLastEpisode.collectAsState(initial = false)
    val defaultFilter by settings.defaultFilter.collectAsState(initial = "All")
    val subAlways by settings.subtitleAlwaysShow.collectAsState(initial = false)
    val subSize by settings.subtitleSize.collectAsState(initial = "Medium")
    val subColor by settings.subtitleColor.collectAsState(initial = "White")
    val subBgStored by settings.subtitleBackground.collectAsState(initial = "Semi-black")
    val user by vm.currentUser.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    var hashVisible by remember { mutableStateOf(false) }
    var credStatus by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }

    fun setValue(key: String, value: Any) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            when (key) {
                "autoplay" -> settings.setAutoplay(value as Boolean)
                "smart" -> settings.setSmartFilter(value as Boolean)
                "openLast" -> settings.setOpenLastEpisode(value as Boolean)
                "defaultFilter" -> settings.setDefaultFilter(value as String)
                "subAlways" -> settings.setSubtitleAlwaysShow(value as Boolean)
                "subSize" -> settings.setSubtitleSize(value as String)
                "subColor" -> settings.setSubtitleColor(value as String)
                "subBg" -> settings.setSubtitleBackground(value as String)
            }
        }
    }

    // HTML subtitle-background labels <-> SettingsStore stored values.
    val subBgLabel = when (subBgStored) {
        "Black" -> "Solid black"
        "Transparent" -> "None"
        else -> "Translucent"
    }
    val subBgStoredFor = { label: String ->
        when (label) {
            "Solid black" -> "Black"
            "None" -> "Transparent"
            else -> "Semi-black"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NsColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NsDimens.ContentPadding)
    ) {
        NsTopBar(onAdd = { showAddSheet = true }, onRefresh = { vm.refreshGroups() })
        Text(
            "Settings",
            fontFamily = SoraFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
            color = NsColors.TextPrimary,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Playback aur subtitles — sab ek jagah, saaf groups me",
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            color = NsColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        SetLabel("Playback")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NsDimens.CornerCard))
                .background(NsColors.Surface)
                .border(1.dp, NsColors.Divider, RoundedCornerShape(NsDimens.CornerCard))
        ) {
            SetSwitchRow(
                icon = NsIcons.Play,
                title = "Autoplay next video",
                desc = "Play the next video automatically when one ends.",
                checked = autoplay,
                onChange = { setValue("autoplay", it) }
            )
            SetDivider()
            SetSwitchRow(
                icon = NsIcons.FilterLines,
                title = "Smart Filter",
                desc = "Show each episode once, in its best quality. Turn off to see every copy.",
                checked = smart,
                onChange = { setValue("smart", it) }
            )
            SetDivider()
            SetValueRow(
                icon = NsIcons.FilterLines,
                title = "Default quality filter",
                desc = "The filter new channels open with.",
                value = defaultFilter,
                onClick = { sheet = "quality" }
            )
            SetDivider()
            SetSwitchRow(
                icon = NsIcons.Play,
                title = "Open last episode on start",
                desc = "Jump straight to the episode you were watching when the app opens.",
                checked = openLast,
                onChange = { setValue("openLast", it) }
            )
        }

        SetLabel("Subtitles")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NsDimens.CornerCard))
                .background(NsColors.Surface)
                .border(1.dp, NsColors.Divider, RoundedCornerShape(NsDimens.CornerCard))
        ) {
            SetSwitchRow(
                icon = NsIcons.Captions,
                title = "Always show subtitles",
                desc = "Show subtitles automatically when a video has them.",
                checked = subAlways,
                onChange = { setValue("subAlways", it) }
            )
            SetDivider()
            SetValueRow(
                icon = NsIcons.TextSize,
                title = "Subtitle size",
                desc = "How big subtitles appear on the video.",
                value = subSize,
                onClick = { sheet = "subsize" }
            )
            SetDivider()
            SetValueRow(
                icon = NsIcons.ColourHalf,
                title = "Subtitle colour",
                desc = "Text colour for subtitles.",
                value = subColor,
                onClick = { sheet = "subcolor" }
            )
            SetDivider()
            SetValueRow(
                icon = NsIcons.SubBackground,
                title = "Subtitle background",
                desc = "Band behind the subtitle text.",
                value = subBgLabel,
                onClick = { sheet = "subbg" }
            )
            // Live subtitle preview (16/7.5, caption centred bottom, live style).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 2.dp)
                    .aspectRatio(16f / 7.5f)
                    .clip(RoundedCornerShape(NsDimens.CornerThumb))
                    .background(NsColors.SurfaceVariant),
                contentAlignment = Alignment.BottomCenter
            ) {
                val sizeSp = when (subSize) {
                    "Small" -> 12.sp
                    "Large" -> 18.sp
                    else -> 14.5.sp
                }
                val col = when (subColor) {
                    "Yellow" -> Color(0xFFFFD94D)
                    "Cyan" -> Color(0xFF6FE7F5)
                    else -> Color.White
                }
                val bg = when (subBgLabel) {
                    "None" -> Color.Transparent
                    "Solid black" -> Color.Black
                    else -> Color(0x8C000000)
                }
                Box(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        "Ye raha tumhara subtitle preview",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = sizeSp,
                        color = col
                    )
                }
            }
        }

        SetLabel("About")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NsDimens.CornerCard))
                .background(NsColors.Surface)
                .border(1.dp, NsColors.Divider, RoundedCornerShape(NsDimens.CornerCard))
        ) {
            SetValueRow(
                icon = NsIcons.Play,
                title = "NobiStream",
                desc = "Videos stream from your own Telegram account with TDLib (MTProto). No videos are uploaded anywhere.",
                value = "1.1.1 (4)",
                valueColor = NsColors.TextSecondary,
                clickable = false,
                onClick = {}
            )
            SetDivider()
            SetSimpleRow(
                title = "Account",
                desc = user?.let { "Signed in as ${it.displayName}" } ?: "Not signed in"
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "NobiStream 2 sends nothing to any server except Telegram, using your own account. Your groups and watch positions stay on this phone. Thanks and all credit goes to Telegram / TDLib and their developers.",
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp,
            color = NsColors.TextSecondary
        )

        // Telegram credentials (kept: needed when no built-in credentials exist).
        SetLabel("Telegram")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NsDimens.CornerCard))
                .background(NsColors.Surface)
                .border(1.dp, NsColors.Divider, RoundedCornerShape(NsDimens.CornerCard))
                .padding(16.dp)
        ) {
            val credState = when {
                repo.credentialsStore.hasSaved -> "Credentials are saved on this phone."
                repo.credentialsStore.hasBuiltIn -> "Credentials are built into this install."
                else -> "No credentials saved yet."
            }
            Text(credState, fontFamily = ManropeFamily, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = NsColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Get free credentials at my.telegram.org (API development tools). Never share them with anyone.",
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp,
                color = NsColors.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = apiId,
                onValueChange = { apiId = it.filter { c -> c.isDigit() } },
                label = { Text("api_id") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().height(NsDimens.InputHeight)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiHash,
                onValueChange = { apiHash = it },
                label = { Text("api_hash") },
                visualTransformation = if (hashVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { hashVisible = !hashVisible }) {
                        Icon(if (hashVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Show api_hash")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().height(NsDimens.InputHeight)
            )
            Spacer(Modifier.height(12.dp))
            Row {
                PrimaryButton(
                    text = "Save",
                    onClick = {
                        val id = apiId.toIntOrNull()
                        if (id == null || apiHash.isBlank()) {
                            credStatus = "Enter both api_id and api_hash."
                        } else {
                            repo.credentialsStore.save(id, apiHash)
                            credStatus = "Saved"
                            apiId = ""
                            apiHash = ""
                        }
                    }
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    repo.credentialsStore.clearSaved()
                    credStatus = "Credentials are built into this install."
                }) { Text("Use built-in credentials instead") }
            }
            if (credStatus != null) {
                Text(credStatus!!, style = MaterialTheme.typography.bodyMedium, color = NsColors.Success)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmClearCache = true }) { Text("Clear cache") }
            TextButton(onClick = { confirmLogout = true }) {
                Text("Log out of Telegram", color = NsColors.Error)
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    when (sheet) {
        "quality" -> NsOptionSheet(
            title = "Default quality filter",
            subtitle = "The filter new channels open with.",
            options = listOf("All", "480p", "720p", "1080p", "4K"),
            selected = defaultFilter,
            onSelect = { setValue("defaultFilter", it); sheet = null; vm.showSnackbar("Default quality filter — $it") },
            onDismiss = { sheet = null }
        )
        "subsize" -> NsOptionSheet(
            title = "Subtitle size",
            subtitle = "How big subtitles appear on the video.",
            options = listOf("Small", "Medium", "Large"),
            selected = subSize,
            onSelect = { setValue("subSize", it); sheet = null; vm.showSnackbar("Subtitle size — $it") },
            onDismiss = { sheet = null }
        )
        "subcolor" -> NsOptionSheet(
            title = "Subtitle colour",
            subtitle = "Text colour for subtitles.",
            options = listOf("White", "Yellow", "Cyan"),
            selected = subColor,
            onSelect = { setValue("subColor", it); sheet = null; vm.showSnackbar("Subtitle colour — $it") },
            onDismiss = { sheet = null }
        )
        "subbg" -> NsOptionSheet(
            title = "Subtitle background",
            subtitle = "Band behind the subtitle text.",
            options = listOf("None", "Translucent", "Solid black"),
            selected = subBgLabel,
            onSelect = { setValue("subBg", subBgStoredFor(it)); sheet = null; vm.showSnackbar("Subtitle background — $it") },
            onDismiss = { sheet = null }
        )
    }

    if (showAddSheet) {
        AddGroupSheet(vm = vm, onDismiss = { showAddSheet = false })
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "Log out?",
            body = "Your saved groups stay, but you will need to sign in again to watch videos.",
            confirmText = "Log out",
            destructive = true,
            onConfirm = { confirmLogout = false; vm.logout {} },
            onDismiss = { confirmLogout = false }
        )
    }
    if (confirmClearCache) {
        ConfirmDialog(
            title = "Clear cache?",
            body = "Cached video data will be deleted. Downloaded videos are not affected.",
            confirmText = "Clear",
            destructive = true,
            onConfirm = {
                confirmClearCache = false
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    vm.repository.clearCache()
                    vm.showSnackbar("Cache cleared")
                }
            },
            onDismiss = { confirmClearCache = false }
        )
    }
}

@Composable
private fun SetLabel(text: String) {
    Text(
        text.uppercase(),
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
        color = NsColors.TextDisabled,
        letterSpacing = 0.13.sp,
        modifier = Modifier.padding(start = 2.dp, top = 22.dp, bottom = 10.dp)
    )
}

@Composable
private fun SetDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NsColors.Divider))
}

@Composable
private fun SetIconTile(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(NsColors.SurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = NsColors.AccentBright, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun SetSwitchRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(15.dp, 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SetIconTile(icon)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = NsColors.TextPrimary)
            Text(desc, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = NsColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(13.dp))
        NsSwitch(checked = checked, onChange = onChange)
    }
}

@Composable
private fun SetValueRow(
    icon: ImageVector,
    title: String,
    desc: String,
    value: String,
    valueColor: Color = NsColors.AccentBright,
    clickable: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(15.dp, 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SetIconTile(icon)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = NsColors.TextPrimary)
            Text(desc, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = NsColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(13.dp))
        Text(value, fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = valueColor)
        if (clickable) {
            Spacer(Modifier.width(4.dp))
            Icon(NsIcons.ChevronRight, contentDescription = null, tint = valueColor, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SetSimpleRow(title: String, desc: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(15.dp, 16.dp)) {
        Text(title, fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = NsColors.TextPrimary)
        Text(desc, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = NsColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}
