package com.nobi.nobistream2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NobiStream 2 design system (Appendix A + Sam's approved graphite-dark
 * YouTube-like theme from NobiStream v9). ONE accent: Telegram blue.
 * Dark only, by decision. All screens use these tokens, never hex literals.
 */
object NsColors {
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceVariant = Color(0xFF232326)
    val SurfaceHigh = Color(0xFF2C2C30)
    val Primary = Color(0xFF229ED9)
    val PrimaryDark = Color(0xFF1B7FB8)
    val PrimaryContainer = Color(0xFF12344A)
    val OnPrimaryContainer = Color(0xFFBFE3F5)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9AA8BC)
    val TextDisabled = Color(0xFF5B6B80)
    val Divider = Color(0xFF2A2A2E)
    val Outline = Color(0xFF3A3A40)
    val Success = Color(0xFF34C77B)
    val Warning = Color(0xFFF5A623)
    val Error = Color(0xFFE5484D)
    val QualityBadge = Color(0xFFFFB020)
    val OnQualityBadge = Color(0xFF000000)
    val Scrim = Color(0xCC000000)
    val Skeleton = Color(0xFF232326)
}

object NsDimens {
    val ContentPadding = 16.dp
    val SectionSpacing = 20.dp
    val ItemSpacing = 12.dp
    val GroupSpacing = 8.dp
    val CornerCard = 16.dp
    val CornerButton = 16.dp
    val CornerSheet = 28.dp
    val CornerThumb = 12.dp
    val TopBarHeight = 64.dp
    val BottomNavHeight = 80.dp
    val ButtonHeight = 56.dp
    val InputHeight = 56.dp
    val AvatarSize = 48.dp
    val IconSize = 24.dp
    val SmallIconSize = 20.dp
    val FabSize = 56.dp
    val VideoCardWidth = 240.dp
    val RecentCardWidth = 210.dp
    val ListThumbWidth = 140.dp
    val MiniPlayerHeight = 72.dp
    val MiniThumbSize = 56.dp
}

private val DarkScheme = darkColorScheme(
    primary = NsColors.Primary,
    onPrimary = Color.White,
    primaryContainer = NsColors.PrimaryContainer,
    onPrimaryContainer = NsColors.OnPrimaryContainer,
    background = NsColors.Background,
    onBackground = NsColors.TextPrimary,
    surface = NsColors.Surface,
    onSurface = NsColors.TextPrimary,
    surfaceVariant = NsColors.SurfaceVariant,
    onSurfaceVariant = NsColors.TextSecondary,
    outline = NsColors.Outline,
    error = NsColors.Error,
    onError = Color.White
)

private val NsTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp)
)

@Composable
fun NobiStream2Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = NsTypography,
        content = content
    )
}
// Dark-only by decision (Sam approved graphite dark); the single dark
// scheme above is used regardless of the system theme setting.
