package com.nobi.nobistream2.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nobi.nobistream2.BuildConfig
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.components.AppTopBar
import com.nobi.nobistream2.ui.components.ConfirmDialog
import com.nobi.nobistream2.ui.components.PrimaryButton
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens

/**
 * Settings (Appendix A16): grouped sections, 24dp section spacing,
 * 64dp rows, 16sp title + 12sp description. Credential editor masks
 * the hash; full secrets are never displayed.
 */
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val repo = vm.repository
    val settings = repo.settingsStore
    val autoplay by settings.autoplay.collectAsState(initial = true)
    val smart by settings.smartFilter.collectAsState(initial = true)
    val openLast by settings.openLastEpisode.collectAsState(initial = false)
    val cacheMb by settings.cacheSizeMb.collectAsState(initial = 500)
    val user by vm.currentUser.collectAsState()
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    var hashVisible by remember { mutableStateOf(false) }
    var credStatus by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }

    val credState = when {
        repo.credentialsStore.hasSaved -> "Credentials are saved on this phone."
        repo.credentialsStore.hasBuiltIn -> "Credentials are built into this install."
        else -> "No credentials saved yet."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(title = "Settings", onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = NsDimens.ContentPadding)) {
            SettingsSection("Telegram")
            SettingsRow("Account", user?.let { "Signed in as ${it.displayName}" } ?: "Not signed in")
            Spacer(Modifier.height(12.dp))
            Text("Telegram API credentials", style = MaterialTheme.typography.titleMedium, color = NsColors.TextPrimary)
            Text(
                "Get free credentials at my.telegram.org (API development tools). Never share them with anyone.",
                style = MaterialTheme.typography.bodySmall,
                color = NsColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(8.dp))
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
            Text(credState, style = MaterialTheme.typography.bodySmall, color = NsColors.TextSecondary)

            SettingsSection("Playback")
            SwitchRow("Autoplay next video", "Play the next video automatically when one ends.", autoplay) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    settings.setAutoplay(it)
                }
            }
            SwitchRow("Smart Filter", "Show each episode once, in its best quality. Turn off to see every copy.", smart) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    settings.setSmartFilter(it)
                }
            }
            SwitchRow("Open last episode on start", "Jump straight to the episode you were watching when the app opens.", openLast) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    settings.setOpenLastEpisode(it)
                }
            }

            SettingsSection("Storage")
            SettingsRow("Video cache size", if (cacheMb >= 1000) "1 GB" else "500 MB")
            Row {
                TextButton(onClick = {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        settings.setCacheSizeMb(500)
                    }
                }) { Text("500 MB") }
                TextButton(onClick = {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        settings.setCacheSizeMb(1000)
                    }
                }) { Text("1 GB") }
            }
            TextButton(onClick = { confirmClearCache = true }) { Text("Clear cache") }

            SettingsSection("Account")
            TextButton(onClick = { confirmLogout = true }) {
                Text("Log out of Telegram", color = NsColors.Error)
            }

            SettingsSection("Appearance")
            SettingsRow("Theme", "Dark (only theme)")

            SettingsSection("About")
            SettingsRow("Version", "1.0.0 (1)")
            Text(
                "NobiStream 2. Videos stream from your own Telegram account with TDLib (MTProto). No videos are uploaded anywhere. Thanks and all credit goes to Telegram / TDLib and their developers.",
                style = MaterialTheme.typography.bodySmall,
                color = NsColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "NobiStream 2 sends nothing to any server except Telegram, using your own account. Your groups and watch positions stay on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = NsColors.TextSecondary
            )
            Spacer(Modifier.height(32.dp))
        }
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
private fun SettingsSection(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = NsColors.TextPrimary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsRow(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = NsColors.TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NsColors.TextSecondary)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = NsColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NsColors.TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
