package com.nobi.nobistream2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobi.nobistream2.R

/**
 * NobiStream 2 v1.1.0 design system — the approved HTML redesign, DITTO.
 * Theme tokens are taken EXACTLY from the HTML `:root` (page #060B14,
 * app bg #0B1220, surface #121E33, surface2 #182842, accent #229ED9,
 * accent-bright #3AB6F0). Dark only, by decision — the HTML is dark-only.
 * Fonts are Manrope (body) + Sora (headings), bundled as static TTFs
 * under res/font/ (no runtime download). All screens use these tokens,
 * never hex literals.
 */
object NsColors {
    val Page = Color(0xFF060B14)
    val Background = Color(0xFF0B1220)
    val Surface = Color(0xFF121E33)
    val SurfaceVariant = Color(0xFF182842)
    val SurfaceHigh = Color(0xFF1B2C47)
    val Primary = Color(0xFF229ED9)
    val PrimaryDark = Color(0xFF1677B8)
    val PrimaryContainer = Color(0xFF14324A)
    val OnPrimaryContainer = Color(0xFFBFE3F5)
    val AccentBright = Color(0xFF3AB6F0)
    val AccentGradientStart = Color(0xFF2FB9F2)
    val AccentGradientEnd = Color(0xFF1677B8)
    val TextPrimary = Color(0xFFF2F7FC)
    val TextSecondary = Color(0xFF93A6BC)
    val TextDisabled = Color(0xFF647A94)
    val TextOnHero = Color(0xFFC7D6E6)
    val TimeRow = Color(0xFFD7E4F2)
    /** rgba(147,166,188,.14) — the 1px line colour everywhere. */
    val Divider = Color(0x2493A6BC)
    val Outline = Color(0xFF33465F)
    val SwitchTrackOff = Color(0xFF33465F)
    val Success = Color(0xFF34C77B)
    val Warning = Color(0xFFF5A623)
    val Error = Color(0xFFE5484D)
    val QualityBadge = Color(0xFFFFB020)
    val OnQualityBadge = Color(0xFF000000)
    /** Dark 82% badge background used on thumbnails. */
    val BadgeBg = Color(0xD2040814)
    val SheetBg = Color(0xFF101C30)
    val BottomNavBg = Color(0xF50F1A2D)
    val ToastBg = Color(0xFFEAF5FD)
    val ToastText = Color(0xFF0B2237)
    val Scrim = Color(0xA803060B)
    val Skeleton = Color(0xFF232326)
}

val ManropeFamily = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
    Font(R.font.manrope_800, FontWeight.ExtraBold)
)

val SoraFamily = FontFamily(
    Font(R.font.sora_600, FontWeight.SemiBold),
    Font(R.font.sora_700, FontWeight.Bold),
    Font(R.font.sora_800, FontWeight.ExtraBold)
)

object NsDimens {
    val ContentPadding = 20.dp
    val ScreenTopPadding = 6.dp
    val ScreenBottomPadding = 24.dp
    val SectionSpacing = 26.dp
    val SectionHeadBottom = 12.dp
    val ItemSpacing = 12.dp
    val GroupSpacing = 8.dp
    val CornerCard = 18.dp
    val CornerButton = 16.dp
    val CornerSheet = 26.dp
    val CornerThumb = 12.dp
    val TopBarHeight = 64.dp
    val BottomNavHeight = 80.dp
    val ButtonHeight = 46.dp
    val InputHeight = 50.dp
    val AvatarSize = 52.dp
    val ChannelAvatarSize = 38.dp
    val IconSize = 24.dp
    val SmallIconSize = 20.dp
    val FabSize = 54.dp
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
    displayLarge = TextStyle(fontFamily = SoraFamily, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontFamily = SoraFamily, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
    titleMedium = TextStyle(fontFamily = SoraFamily, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold),
    titleSmall = TextStyle(fontFamily = ManropeFamily, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
    bodyLarge = TextStyle(fontFamily = ManropeFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontFamily = ManropeFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodySmall = TextStyle(fontFamily = ManropeFamily, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontFamily = ManropeFamily, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold),
    labelSmall = TextStyle(fontFamily = ManropeFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
)

@Composable
fun NobiStream2Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = NsTypography,
        content = content
    )
}
// Dark-only by decision (the HTML redesign is dark navy throughout);
// the single dark scheme above is used regardless of the system theme.
