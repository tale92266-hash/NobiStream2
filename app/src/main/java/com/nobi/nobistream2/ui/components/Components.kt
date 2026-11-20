package com.nobi.nobistream2.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nobi.nobistream2.core.FormatUtils
import com.nobi.nobistream2.core.GroupItem
import com.nobi.nobistream2.core.VideoItem
import com.nobi.nobistream2.core.VideoQuality
import com.nobi.nobistream2.data.NobiRepository
import com.nobi.nobistream2.ui.AppViewModel
import com.nobi.nobistream2.ui.ThumbCache
import com.nobi.nobistream2.ui.theme.ManropeFamily
import com.nobi.nobistream2.ui.theme.NsColors
import com.nobi.nobistream2.ui.theme.NsDimens
import com.nobi.nobistream2.ui.theme.NsIcons
import com.nobi.nobistream2.ui.theme.SoraFamily
import java.io.File

/** HTML top bar: gradient logo mark + "NobiStream" + "+ Add" pill + refresh circle. */
@Composable
fun NsTopBar(
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rotation by remember { mutableStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = tween(300),
        label = "refreshRotate"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NsColors.AccentGradientStart, NsColors.AccentGradientEnd),
                            start = Offset(0f, 0f),
                            end = Offset(36f, 36f)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    NsIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(19.dp)
                        .padding(start = 2.dp)
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                buildAnnotatedString {
                    append("Nobi")
                    withStyle(SpanStyle(color = NsColors.AccentBright)) { append("Stream") }
                },
                fontFamily = SoraFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
                color = NsColors.TextPrimary,
                maxLines = 1
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(NsColors.SurfaceVariant)
                .border(1.dp, NsColors.Divider, RoundedCornerShape(999.dp))
                .clickable(onClick = onAdd)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(NsIcons.Plus, contentDescription = null, tint = NsColors.AccentBright, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = NsColors.TextPrimary)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NsColors.Surface)
                .border(1.dp, NsColors.Divider, CircleShape)
                .clickable {
                    rotation += 40f
                    onRefresh()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                NsIcons.Refresh,
                contentDescription = "Refresh",
                tint = NsColors.TextPrimary,
                modifier = Modifier
                    .size(19.dp)
                    .rotate(animatedRotation)
            )
        }
    }
}

/** One consistent top bar for sub-screens (back + title). Title ellipsizes, never collides. */
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    // v1.1.1 item 8: the title gets TWO lines and the bar grows with it
    // (a 1-line title + four 48dp actions truncated channel names
    // mid-word). Actions stay 40dp (the HTML's icon-btn size).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NsDimens.ContentPadding, vertical = 8.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NsColors.TextPrimary)
            }
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = NsColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

/** Primary button — HTML pill, 46dp, accent bg, 14.5sp 800, shadow handled by caller. */
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
        shape = RoundedCornerShape(999.dp),
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
        Text(text, fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp)
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
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NsColors.TextPrimary)
    ) {
        Text(text, fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp)
    }
}

/** Group avatar: real Telegram photo when available, else the initial letter. */
@Composable
fun GroupAvatar(
    group: GroupItem,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    withRing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val path = group.photoLocalPath
    val fileExists = path != null && File(path).exists()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (withRing) Modifier.border(2.dp, Color(0x8058B6F0), CircleShape)
                else Modifier
            )
            .background(NsColors.PrimaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (fileExists) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(File(path!!)).crossfade(200).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Text(
                group.title.firstOrNull()?.uppercase() ?: "?",
                fontFamily = SoraFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
                color = NsColors.OnPrimaryContainer
            )
        }
    }
}

/** Thumbnail with the ThumbCache fallback chain; placeholder is a play glyph on surface, never a fake image. */
@Composable
fun VideoThumb(
    repo: NobiRepository,
    item: VideoItem,
    modifier: Modifier = Modifier,
    showBadges: Boolean = true
) {
    var path by remember(item.chatId, item.messageId) { mutableStateOf<String?>(item.thumbLocalPath) }
    androidx.compose.runtime.LaunchedEffect(item.chatId, item.messageId) {
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
            Icon(NsIcons.Play, contentDescription = null, tint = NsColors.TextDisabled, modifier = Modifier.size(32.dp))
        }
        if (showBadges) {
            // HTML badges: quality bottom-left (accent-bright on dark 82%), duration bottom-right (white).
            if (item.quality != VideoQuality.UNKNOWN) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(NsColors.BadgeBg)
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(item.quality.label, fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = NsColors.AccentBright)
                }
            }
            if (item.durationSec > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(NsColors.BadgeBg)
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(FormatUtils.formatDuration(item.durationSec), fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

/** HTML episode card: 16/9 thumb + centre play FAB + copies badge + channel info row. */
@Composable
fun EpisodeCard(
    repo: NobiRepository,
    item: VideoItem,
    group: GroupItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onCopiesClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(bottom = 26.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            VideoThumb(repo, item, modifier = Modifier.fillMaxSize())
            if (item.hiddenCopies > 0) {
                // v1.1.1 item 11: the copies badge is a real button —
                // tap it to pick any copy (best is the default tap).
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NsColors.BadgeBg)
                        .then(if (onCopiesClick != null) Modifier.clickable(onClick = onCopiesClick) else Modifier)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        "${item.hiddenCopies + 1} copies",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.5.sp,
                        color = Color.White
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0x8C060B14))
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(NsIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp).padding(start = 3.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            if (group != null) {
                GroupAvatar(group = group, size = 38.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp, color = NsColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(
                    buildAnnotatedString {
                        append(FormatUtils.formatDate(item.dateEpochSec))
                        if (item.sizeBytes > 0) append(" · ${FormatUtils.formatSize(item.sizeBytes)}")
                        if (group != null && group.username.isNotBlank()) {
                            append(" · ")
                            withStyle(SpanStyle(color = NsColors.AccentBright, fontWeight = FontWeight.Bold)) {
                                append("@${group.username}")
                            }
                        }
                    },
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = NsColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * v1.1.1 item 11: the copies picker. In v1.1.0 the copies badge was
 * display-only and no sheet existed at all, so a user could never pick
 * a specific copy — this sheet is the "pick any copy directly" path.
 * Copies are best-first; the first row carries the "Best" tag. The
 * caller plays the picked copy as-is (no best-copy redirection).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopiesSheet(
    copies: List<VideoItem>,
    onPick: (VideoItem) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Choose a copy", style = MaterialTheme.typography.titleLarge, color = NsColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "${copies.size} copies — best first",
                style = MaterialTheme.typography.bodySmall,
                color = NsColors.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            copies.forEachIndexed { index, copy ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(52.dp).clickable { onPick(copy) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            copy.quality.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = NsColors.TextPrimary
                        )
                        Text(
                            "${FormatUtils.formatSize(copy.sizeBytes)} · ${FormatUtils.formatDate(copy.dateEpochSec)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NsColors.TextSecondary
                        )
                    }
                    if (index == 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(NsColors.Primary.copy(alpha = 0.14f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "Best",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = NsColors.AccentBright
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** HTML mini-card for the Home "Recently Added" 2-column rail. */
@Composable
fun MiniVideoCard(
    repo: NobiRepository,
    item: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        VideoThumb(repo, item, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Text(
            item.title,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.5.sp,
            color = NsColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp)
        )
        Text(
            listOfNotNull(
                FormatUtils.formatDate(item.dateEpochSec).takeIf { it.isNotEmpty() },
                FormatUtils.formatSize(item.sizeBytes).takeIf { item.sizeBytes > 0 }
            ).joinToString(" · "),
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = NsColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * ONE VideoCard for every list — Continue Watching, Search, Watch Later,
 * Downloads, Channel. Same card everywhere (row form, thumb + title + meta).
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
            Text(item.title, fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = NsColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    FormatUtils.formatDate(item.dateEpochSec).takeIf { it.isNotEmpty() },
                    FormatUtils.formatSize(item.sizeBytes).takeIf { item.sizeBytes > 0 },
                    if (item.hiddenCopies > 0) "${item.hiddenCopies + 1} copies" else null
                ).joinToString(" · "),
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = NsColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.watched) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = NsColors.Success, modifier = Modifier.size(14.dp))
                    Text("Watched", fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = NsColors.Success)
                }
            }
        }
    }
}

/** HTML group row: surface bg, 1px line, radius 18, 52dp ringed avatar, chevron. */
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
            .clip(RoundedCornerShape(NsDimens.CornerCard))
            .background(NsColors.Surface)
            .border(1.dp, NsColors.Divider, RoundedCornerShape(NsDimens.CornerCard))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupAvatar(group = group, size = 52.dp, withRing = true)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(group.title, fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = NsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${group.videoCount} videos" + if (group.lastUpdatedEpochSec > 0) " · Updated ${FormatUtils.formatDate(group.lastUpdatedEpochSec)}" else "",
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = NsColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(NsIcons.ChevronRight, contentDescription = null, tint = NsColors.TextDisabled, modifier = Modifier.size(18.dp))
    }
}

/** HTML quality chip — 40dp pill, check icon + shadow when on, really filters. */
@Composable
fun QualityChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) NsColors.Primary else NsColors.Surface)
            .then(
                if (!selected) Modifier.border(1.dp, NsColors.Divider, RoundedCornerShape(999.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(NsIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.5.sp,
            color = if (selected) Color.White else NsColors.TextSecondary,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun SectionHeader(title: String, count: String? = null, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontFamily = SoraFamily, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = NsColors.TextPrimary)
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                Text(count, fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = NsColors.TextDisabled)
            }
        }
        action?.invoke()
    }
}

/** HTML switch: 50x29, track #33465F off / accent on, 23dp white knob. */
@Composable
fun NsSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 3.dp,
        animationSpec = tween(220),
        label = "switchKnob"
    )
    Box(
        modifier = modifier
            .size(50.dp, 29.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) NsColors.Primary else NsColors.SwitchTrackOff)
            .clickable { onChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset, y = 3.dp)
                .size(23.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** HTML bottom sheet shell: #101C30 bg, 26dp top radius, 42x5 grab handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NsSheet(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NsColors.SheetBg,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 18.dp)
                    .size(42.dp, 5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(NsColors.Outline)
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 24.dp)) {
            Text(title, fontFamily = SoraFamily, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = NsColors.TextPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = NsColors.TextSecondary)
            }
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

/** HTML option sheet: rows 52dp, selected row accent-bright + 22dp accent tick circle. */
@Composable
fun NsOptionSheet(
    title: String,
    subtitle: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    NsSheet(onDismiss = onDismiss, title = title, subtitle = subtitle) {
        Column(modifier = Modifier.fillMaxWidth()) {
            options.forEach { option ->
                val isSel = option == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable { onSelect(option) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        option,
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = if (isSel) NsColors.AccentBright else NsColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSel) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(NsColors.Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(NsIcons.Tick, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
                if (option != options.last()) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NsColors.Divider))
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = NsColors.TextSecondary)
            }
        }
    }
}

/** HTML Add Group sheet — validates immediately, no green success before TDLib confirms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val valid = remember(link) {
        val t = link.trim()
        t.startsWith("https://t.me/") || t.startsWith("t.me/") || t.startsWith("@") || t.matches(Regex("[A-Za-z0-9_]{5,}"))
    }
    NsSheet(
        onDismiss = onDismiss,
        title = "Add Group / Channel",
        subtitle = "Telegram group ya channel ka link paste karo. Uske saare videos ek saaf list me aa jayenge."
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NsColors.Surface)
                .border(1.5.dp, NsColors.AccentBright.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(NsIcons.Link, contentDescription = null, tint = NsColors.AccentBright, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = link,
                onValueChange = {
                    link = it
                    status = when {
                        it.isBlank() -> null
                        valid -> "Link looks valid — tap Add to confirm with Telegram."
                        else -> "That doesn't look like a Telegram link. Paste a t.me link or a @username."
                    }
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = NsColors.TextPrimary
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (link.isEmpty()) {
                        Text("https://t.me/…", fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = NsColors.TextDisabled)
                    }
                    inner()
                }
            )
        }
        if (status != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                status!!,
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = if (valid) NsColors.Success else NsColors.Error
            )
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = "Add Group",
            onClick = {
                busy = true
                status = null
                vm.addGroup(
                    link,
                    onAdded = { onDismiss() },
                    onError = { message -> status = message; busy = false }
                )
            },
            enabled = valid && !busy,
            loading = busy,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = NsColors.TextSecondary)
        }
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
                .size(118.dp)
                .drawBehind {
                    drawCircle(
                        color = Color(0x5958B6F0),
                        radius = size.maxDimension / 2 + 13.dp.toPx(),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                        )
                    )
                }
                .clip(CircleShape)
                .background(NsColors.SurfaceVariant)
                .border(1.dp, NsColors.Divider, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = NsColors.AccentBright, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, fontFamily = SoraFamily, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, color = NsColors.TextPrimary)
        Spacer(Modifier.height(9.dp))
        Text(
            subtitle,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = NsColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (buttonText != null && onButtonClick != null) {
            Spacer(Modifier.height(24.dp))
            PrimaryButton(text = buttonText, onClick = onButtonClick, modifier = Modifier.fillMaxWidth())
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
        Text(title, fontFamily = SoraFamily, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = NsColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(body, fontFamily = ManropeFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = NsColors.TextSecondary)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "Retry", onClick = onRetry)
    }
}

/** Skeleton — dim shimmer, never excessively bright. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(900),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
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

/** v1.1.1 item 7: shimmer thumb + title lines for the channel grid skeleton. */
@Composable
fun SkeletonGridCard(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmerGrid")
    val alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(900),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "shimmerGridAlpha"
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(NsDimens.CornerThumb)).background(NsColors.Skeleton.copy(alpha = alpha)))
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(NsColors.Skeleton.copy(alpha = alpha)))
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(NsColors.Skeleton.copy(alpha = alpha)))
    }
}

/**
 * v1.1.1 item 7: infinite scroll with a visible fallback. The footer
 * auto-loads once when it enters composition — gated: it only exists
 * when the list is non-empty and hasMore is true (the NobiStream 1
 * v2.6.3 lesson: an ungated trigger fires spuriously on an empty
 * list). The button stays visible as the fallback when a page fails
 * or auto-load stalls.
 */
@Composable
fun LoadMoreFooter(loadingMore: Boolean, failed: Boolean, onLoadMore: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loadingMore) {
            Text("Loading more videos…", style = MaterialTheme.typography.bodyMedium, color = NsColors.TextSecondary)
        } else {
            Button(onClick = onLoadMore) {
                Text(if (failed) "Retry" else "Load more")
            }
            androidx.compose.runtime.LaunchedEffect(Unit) { onLoadMore() }
        }
    }
}

/** Material 3 dialog — clear title, description, Cancel + Confirm. Destructive text is differentiated. */
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
