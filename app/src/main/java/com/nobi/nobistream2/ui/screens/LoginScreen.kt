package com.nobi.nobistream2.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nobi.nobistream2.core.AuthState
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.components.PrimaryButton
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Login: phone (+91) -> Telegram code (6 boxes, auto-advance, paste fills
 * all) -> 2FA password. Wrong code shakes the row gently. No resume
 * dialog anywhere in the app — playback resumes silently (PlayerSession).
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LoginScreen(
    vm: AppViewModel,
    onSignedIn: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val authState by vm.authState.collectAsState()
    val repo = vm.repository
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var shakeTrigger by remember { mutableStateOf(0) }
    val shake by animateFloatAsState(
        targetValue = if (shakeTrigger % 2 == 0) 0f else 1f,
        label = "shake"
    )
    val hasCreds = remember { repo.credentialsStore.hasCredentials() }

    LaunchedEffect(authState) {
        if (authState == AuthState.READY) onSignedIn()
    }
    LaunchedEffect(Unit) {
        vm.ensureStarted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NsDimens.ContentPadding, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = NsColors.Primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("NobiStream 2", style = MaterialTheme.typography.titleLarge, color = NsColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in with Telegram",
            style = MaterialTheme.typography.titleMedium,
            color = NsColors.TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Use your own Telegram account to watch videos from groups and channels you can already read.",
            style = MaterialTheme.typography.bodyMedium,
            color = NsColors.TextSecondary
        )
        Spacer(Modifier.height(32.dp))

        if (!hasCreds && authState == AuthState.NEED_API_KEYS) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NsColors.SurfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("API credentials needed", style = MaterialTheme.typography.titleMedium, color = NsColors.Warning)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Open Settings and enter the free api_id and api_hash from my.telegram.org first. They are stored only on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NsColors.TextSecondary
                    )
                    TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        AnimatedContent(
            targetState = authState,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
            },
            label = "loginStep"
        ) { state ->
            when (state) {
                AuthState.NEED_PHONE, AuthState.NEED_API_KEYS, AuthState.UNKNOWN, AuthState.LOGGED_OUT -> {
                    Column {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it.filter { c -> c.isDigit() }.take(10) },
                            label = { Text("Phone number") },
                            prefix = { Text("+91 ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(NsDimens.InputHeight)
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            text = "Continue",
                            onClick = {
                                busy = true
                                error = null
                                vm.repository.let { r ->
                                    // Fire and observe authState transitions.
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                        runCatching { r.sendPhoneNumber("+91$phone") }
                                            .onFailure {
                                                error = "Sign-in failed. Check the number and try again."
                                                busy = false
                                            }
                                    }
                                }
                            },
                            enabled = phone.length == 10,
                            loading = busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                AuthState.NEED_CODE -> {
                    Column {
                        // 6-box OTP: one field, auto-advance is native to the
                        // keyboard; paste fills all boxes at once via onValueChange.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { translationX = shake * 8f },
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            repeat(6) { i ->
                                OutlinedTextField(
                                    value = code.getOrNull(i)?.toString() ?: "",
                                    onValueChange = { v ->
                                        if (v.length == 6) { code = v.filter { it.isDigit() }.take(6) }
                                        else if (v.isNotEmpty() && v.last().isDigit()) {
                                            code = (code + v.last()).take(6)
                                        } else if (v.isEmpty() && code.length > i) {
                                            code = code.dropLast(1)
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(error!!, style = MaterialTheme.typography.bodyMedium, color = NsColors.Error)
                        }
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            text = "Verify",
                            onClick = {
                                busy = true
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    runCatching { repo.sendCode(code) }
                                        .onFailure {
                                            error = "Wrong code, try again"
                                            shakeTrigger++
                                            busy = false
                                            delay(300)
                                        }
                                }
                            },
                            enabled = code.length == 6,
                            loading = busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Didn't get a code? Check the number and ask Telegram to resend it from your Telegram app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NsColors.TextSecondary
                        )
                    }
                }
                AuthState.NEED_PASSWORD -> {
                    Column {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Two-step verification password") },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Show password")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(NsDimens.InputHeight)
                        )
                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(error!!, style = MaterialTheme.typography.bodyMedium, color = NsColors.Error)
                        }
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            text = "Sign in",
                            onClick = {
                                busy = true
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    runCatching { repo.sendPassword(password) }
                                        .onFailure {
                                            error = "Wrong password, try again"
                                            busy = false
                                        }
                                }
                            },
                            enabled = password.isNotEmpty(),
                            loading = busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                AuthState.READY -> {
                    Text("Signed in", color = NsColors.Success)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Settings")
        }
    }
}
