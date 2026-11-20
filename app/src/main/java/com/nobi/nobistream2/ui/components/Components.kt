package com.nobi.nobistream2.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nobi.nobistream2.core.FormatUtils
import com.nobi.nobistream2.core.GroupItem
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.core.VideoQuality
import com.nobi.nobistream2.data.NobiRepository
import com.nobi.nobistream2.ui.ThumbCache
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens
import java.io.File

/** Appendix A2: one consistent top bar. Title ellipsizes, never collides. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NsDimens.TopBarHeight)
            .padding(horizontal = NsDimens.ContentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NsColors.TextPrimary)
            }
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = NsColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

/** Appendix A2: PrimaryButton — 56dp, full width where appropriate, 16dp radius, loading + double-click guard. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(NsDimens.ButtonHeight),
        shape = RoundedCornerShape(NsDimens.CornerButton),
        colors = ButtonDefaults.buttonColors(
            containerColor = NsColors.Primary,
            contentColor = Color.White,
            disabledContainerColor = NsColors.SurfaceHigh,
            disabledContentColor = NsColors.TextDisabled
        )
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(NsDimens.ButtonHeight),
        shape = RoundedCornerShape(NsDimens.CornerButton),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NsColors.TextPrimary)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Thumbnail with the ThumbCache fallback chain; placeholder is a play glyph on surface, never a fake image. */
@Composable
fun VideoThumb(
    repo: NobiRepository,
    item: VideoItem,
    modifier: Modifier = Modifier
) {
    var path by remember(item.chatId, item.messageId) { mutableStateOf<String?>(item.thumbLocalPath) }
    LaunchedEffect(item.chatId, item.messageId) {
        if (path.isNullOrBlank() || !File(path!!).exists()) {
            path = ThumbCache.resolve(repo, item)
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(NsDimens.CornerThumb))
            .background(NsColors.SurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (path != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(path!!))
                    .crossfade(200)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = NsColors.TextDisabled, modifier = Modifier.size(32.dp))
        }
        // Quality badge top-left, duration bottom-right (Appendix A6).
        if (item.quality != VideoQuality.UNKNOWN) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NsColors.QualityBadge)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(item.quality.label, style = MaterialTheme.typography.labelSmall, color = NsColors.OnQualityBadge)
            }
        }
        if (item.durationSec > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(FormatUtils.formatDuration(item.durationSec), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

/**
 * Appendix A2: ONE VideoCard for every list — Continue Watching, Recently
 * Added, Search, Watch Later, Downloads, Channel. Same card everywhere.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    repo: NobiRepository,
    item: VideoItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    showProgress: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(NsDimens.ListThumbWidth)) {
            VideoThumb(repo, item, modifier = Modifier.aspectRatio(16f / 9f))
            if (showProgress && item.progressFraction > 0f && !item.watched) {
                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = NsColors.Primary,
                    trackColor = NsColors.Outline
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = NsColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    FormatUtils.formatDate(item.dateEpochSec).takeIf { it.isNotEmpty() },
                    FormatUtils.formatSize(item.sizeBytes).takeIf { item.sizeBytes > 0 },
                    if (item.hiddenCopies > 0) "${item.hiddenCopies} copies" else null
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = NsColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.watched) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = NsColors.Success, modifier = Modifier.size(14.dp))
                    Text("Watched", style = MaterialTheme.typography.bodySmall, color = NsColors.Success)
                }
            }
        }
    }
}

/** Appendix A2: GroupCard — avatar, name, video count, last updated, sync state. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupCard(
    group: GroupItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(NsDimens.AvatarSize)
                .clip(CircleShape)
                .background(NsColors.PrimaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                group.title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = NsColors.OnPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(group.title, style = MaterialTheme.typography.titleMedium, color = NsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${group.videoCount} videos" + if (group.lastUpdatedEpochSec > 0) " · ${FormatUtils.formatDate(group.lastUpdatedEpochSec)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = NsColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.AutoMirrored.Filled.ChevronRight, contentDescription = null, tint = NsColors.TextSecondary, modifier = Modifier.size(NsDimens.SmallIconSize))
    }
}

/** Appendix A2: QualityChip — same dimensions and typography everywhere. */
@Composable
fun QualityChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NsColors.Primary,
            selectedLabelColor = Color.White,
            containerColor = NsColors.SurfaceVariant,
            labelColor = NsColors.TextSecondary
        )
    )
}

@Composable
fun SectionHeader(title: String, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NsDimens.ContentPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = NsColors.TextPrimary)
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(NsColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = NsColors.TextSecondary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = NsColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = NsColors.TextSecondary)
        if (buttonText != null && onButtonClick != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = buttonText, onClick = onButtonClick)
        }
    }
}

@Composable
fun ErrorState(
    title: String,
    body: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = NsColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = NsColors.TextSecondary)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "Retry", onClick = onRetry)
    }
}

/** Appendix A2: skeleton — dim shimmer, never excessively bright. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(NsDimens.ListThumbWidth).aspectRatio(16f / 9f).clip(RoundedCornerShape(NsDimens.CornerThumb)).background(NsColors.Skeleton.copy(alpha = alpha)))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(NsColors.Skeleton.copy(alpha = alpha)))
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(NsColors.Skeleton.copy(alpha = alpha)))
        }
    }
}

/** Appendix A17: Material 3 dialog — clear title, description, Cancel + Confirm. Destructive text is differentiated. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) NsColors.Error else NsColors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
