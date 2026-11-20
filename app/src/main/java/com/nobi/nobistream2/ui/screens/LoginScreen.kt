package com.nobi.nobistream2.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobi.nobistream2.core.AuthState
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.components.PrimaryButton
import com.nobi.nobistream2.ui.theme.ManropeFamily
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens
import com.nobi.nobistream2.ui.theme.NsIcons
import com.nobi.nobistream2.ui.theme.SoraFamily
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Telegram login codes are 5 digits. */
private const val CODE_LENGTH = 5

/** A submit that never answers must surface an error, never spin forever. */
private const val SUBMIT_TIMEOUT_MS = 30_000L

/**
 * Login: phone (+91) -> Telegram code (5 boxes, one field underneath, so
 * backspace/paste work natively) -> 2FA password. Wrong code shakes the
 * row gently. No resume dialog anywhere in the app — playback resumes
 * silently (PlayerSession).
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
    val scope = rememberCoroutineScope()
    val codeFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(authState) {
        if (authState == AuthState.READY) onSignedIn()
        // A submit's loading flag belongs to its own step. The old code
        // never cleared it on success, so the flag leaked from the phone
        // step into the code step and Verify spun forever, disabled.
        // Every step change clears it, plus stale text and errors.
        busy = false
        error = null
        if (authState != AuthState.NEED_CODE) code = ""
        if (authState != AuthState.NEED_PASSWORD) password = ""
        if (authState == AuthState.NEED_CODE) {
            codeFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    LaunchedEffect(Unit) {
        vm.ensureStarted()
    }

    // v1.1.1 item 6: the login screen in the app's own language — dark
    // navy tokens, Manrope + Sora, logo mark + wordmark, same pill
    // buttons as the rest of the app. The FLOW and the 5-digit OTP
    // field are untouched (style only).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NsColors.Background)
            .padding(horizontal = NsDimens.ContentPadding, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFF2FB9F2), Color(0xFF1677B8))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(NsIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp).padding(start = 3.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append("Nobi")
                withStyle(androidx.compose.ui.text.SpanStyle(color = NsColors.AccentBright)) {
                    append("Stream")
                }
            },
            fontFamily = SoraFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            color = NsColors.TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in with Telegram",
            fontFamily = SoraFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = NsColors.TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Use your own Telegram account to watch videos from groups and channels you can already read.",
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = NsColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        if (!hasCreds && authState == AuthState.NEED_API_KEYS) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(NsDimens.CornerCard))
                    .background(NsColors.Surface)
                    .border(1.dp, NsColors.Divider, RoundedCornerShape(NsDimens.CornerCard))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        "API credentials needed",
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = NsColors.Warning
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Open Settings and enter the free api_id and api_hash from my.telegram.org first. They are stored only on this phone.",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
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
                        ThemedLoginField(
                            value = phone,
                            onValueChange = { phone = it.filter { c -> c.isDigit() }.take(10) },
                            label = "Phone number",
                            prefix = {
                                Text(
                                    "+91 ",
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.5.sp,
                                    color = NsColors.AccentBright
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            text = "Continue",
                            onClick = {
                                if (busy) return@PrimaryButton
                                busy = true
                                error = null
                                scope.launch {
                                    runCatching {
                                        withTimeout(SUBMIT_TIMEOUT_MS) {
                                            repo.sendPhoneNumber("+91$phone")
                                        }
                                    }.onFailure {
                                        error = if (it is TimeoutCancellationException) {
                                            "Timed out. Check your connection and try again."
                                        } else {
                                            "Sign-in failed. Check the number and try again."
                                        }
                                        busy = false
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
                        Text(
                            "Enter the 5-digit code sent to +91 $phone",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NsColors.TextSecondary
                        )
                        Spacer(Modifier.height(16.dp))
                        // ONE editable field under 5 drawn boxes. The old
                        // code used 6 separate fields with no focus handling:
                        // backspace on an empty box fired no value change at
                        // all, focus never moved, paste kept only its last
                        // digit, and Verify needed 6 digits that Telegram
                        // never sends. One field makes backspace, paste and
                        // the keyboard behave natively on a real device.
                        FiveDigitCodeField(
                            code = code,
                            onCodeChange = { code = it },
                            focusRequester = codeFocusRequester,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { translationX = shake * 8f }
                        )
                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(error!!, style = MaterialTheme.typography.bodyMedium, color = NsColors.Error)
                        }
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            text = "Verify",
                            onClick = {
                                if (code.length != CODE_LENGTH || busy) return@PrimaryButton
                                busy = true
                                error = null
                                scope.launch {
                                    runCatching {
                                        withTimeout(SUBMIT_TIMEOUT_MS) { repo.sendCode(code) }
                                    }.onFailure {
                                        error = if (it is TimeoutCancellationException) {
                                            "Timed out. Check your connection and try again."
                                        } else {
                                            "Wrong code, try again"
                                        }
                                        shakeTrigger++
                                        busy = false
                                        delay(400)
                                        shakeTrigger = 0
                                    }
                                    // On success the auth-state change clears
                                    // busy via the LaunchedEffect above.
                                }
                            },
                            enabled = code.length == CODE_LENGTH,
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
                        ThemedLoginField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Two-step verification password",
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Show password")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(error!!, style = MaterialTheme.typography.bodyMedium, color = NsColors.Error)
                        }
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            text = "Sign in",
                            onClick = {
                                if (busy) return@PrimaryButton
                                busy = true
                                error = null
                                scope.launch {
                                    runCatching {
                                        withTimeout(SUBMIT_TIMEOUT_MS) {
                                            repo.sendPassword(password)
                                        }
                                    }.onFailure {
                                        error = if (it is TimeoutCancellationException) {
                                            "Timed out. Check your connection and try again."
                                        } else {
                                            "Wrong password, try again"
                                        }
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

/**
 * v1.1.1 item 6: the app's own input (HTML link-input language:
 * surface bg, 1px line border, 14dp radius, 50dp tall, Manrope).
 * Same value/onValueChange/keyboard contract as the OutlinedTextField
 * it replaces — the login FLOW is untouched, only the look changes.
 */
@Composable
private fun ThemedLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    prefix: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = TextStyle(
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.5.sp,
            color = NsColors.TextPrimary
        ),
        cursorBrush = SolidColor(NsColors.AccentBright),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NsDimens.InputHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NsColors.Surface)
                    .border(1.dp, NsColors.Divider, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (prefix != null) {
                    prefix()
                    Spacer(Modifier.width(2.dp))
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            label,
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.5.sp,
                            color = NsColors.TextDisabled
                        )
                    }
                    innerTextField()
                }
                if (trailingIcon != null) trailingIcon()
            }
        },
        modifier = modifier
    )
}

/**
 * 5-digit code entry: one [BasicTextField] holding the whole code, with 5
 * boxes drawn as its decoration. The single field is what makes the
 * behaviour correct on a real device:
 * - Backspace deletes the last digit natively, including when the caret
 *   sits in an "empty" box (separate per-box fields never even receive
 *   that key event, so backspace did nothing).
 * - Typing a digit fills the next box visually; pasting a 5-digit code
 *   lands in the one field and fills all boxes at once.
 * - The editable field itself is invisible (transparent text, no cursor);
 *   the boxes are its visual representation. The next empty box gets the
 *   primary border plus a caret bar so the user always sees where they are.
 */
@Composable
private fun FiveDigitCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = code,
        onValueChange = { onCodeChange(it.filter { c -> c.isDigit() }.take(CODE_LENGTH)) },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = { innerTextField ->
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(CODE_LENGTH) { i ->
                        val digit = code.getOrNull(i)?.toString() ?: ""
                        val isNext = i == code.length
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(NsDimens.InputHeight)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NsColors.SurfaceVariant)
                                .border(
                                    width = if (isNext) 2.dp else 1.dp,
                                    color = if (isNext) NsColors.Primary else NsColors.Outline,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (digit.isNotEmpty()) {
                                Text(
                                    digit,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = NsColors.TextPrimary
                                )
                            } else if (isNext) {
                                Box(
                                    Modifier
                                        .width(2.dp)
                                        .height(24.dp)
                                        .background(NsColors.Primary)
                                )
                            }
                        }
                    }
                }
                // The real editable field: zero-size and invisible, but
                // still the focused, IME-connected field.
                Box(Modifier.size(0.dp)) { innerTextField() }
            }
        }
    )
}
