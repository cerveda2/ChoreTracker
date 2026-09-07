package cz.dcervenka.choretracker.core.design

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Terracotta = tertiary (overdue/attention), amber (darkened for text contrast) = secondary
// (due soon) - a deliberate swap from Material's own role naming, see the redesign plan's
// Phase 1a token table.
private val WarmLightColors = lightColorScheme(
    primary = Color(0xFF2F5D50),
    onPrimary = Color(0xFFF8F4ED),
    primaryContainer = Color(0xFFD6E9DD),
    onPrimaryContainer = Color(0xFF0E2B22),
    secondary = Color(0xFF8A6A1C),
    onSecondary = Color(0xFFFFFBF0),
    secondaryContainer = Color(0xFFFBE8C2),
    onSecondaryContainer = Color(0xFF3A2A00),
    tertiary = Color(0xFFB15A44),
    onTertiary = Color(0xFFFFF6F3),
    tertiaryContainer = Color(0xFFFFDBD1),
    onTertiaryContainer = Color(0xFF3E0A00),
    background = Color(0xFFF4EEE5),
    onBackground = Color(0xFF201B18),
    surface = Color(0xFFF4EEE5),
    onSurface = Color(0xFF201B18),
    surfaceVariant = Color(0xFFE6DCCF),
    onSurfaceVariant = Color(0xFF5C5048),
    surfaceContainerLowest = Color(0xFFFFFCF7),
    surfaceContainerLow = Color(0xFFFFFCF7),
    surfaceContainer = Color(0xFFEEE6DA),
    surfaceContainerHigh = Color(0xFFE6DCCF),
    surfaceContainerHighest = Color(0xFFDED3C4),
    outline = Color(0xFF8A7C72),
    outlineVariant = Color(0xFFE3DACD),
    scrim = Color(0xFF201B18),
    error = Color(0xFFBA1A1A),
)

private val WarmDarkColors = darkColorScheme(
    primary = Color(0xFFA8D0BD),
    onPrimary = Color(0xFF113228),
    primaryContainer = Color(0xFF24483C),
    onPrimaryContainer = Color(0xFFC3ECD7),
    secondary = Color(0xFFF2C96B),
    onSecondary = Color(0xFF3D2C00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFBE8C2),
    tertiary = Color(0xFFFFB59F),
    onTertiary = Color(0xFF5F1C0A),
    tertiaryContainer = Color(0xFF6E2A18),
    onTertiaryContainer = Color(0xFFFFDBD1),
    background = Color(0xFF171311),
    onBackground = Color(0xFFECE1D7),
    surface = Color(0xFF171311),
    onSurface = Color(0xFFECE1D7),
    surfaceVariant = Color(0xFF51453C),
    onSurfaceVariant = Color(0xFFD4C4B8),
    surfaceContainerLowest = Color(0xFF100D0B),
    surfaceContainerLow = Color(0xFF1C1815),
    surfaceContainer = Color(0xFF241F1B),
    surfaceContainerHigh = Color(0xFF2E2823),
    surfaceContainerHighest = Color(0xFF39322D),
    outline = Color(0xFF9C8D82),
    outlineVariant = Color(0xFF4A4038),
)

@Immutable
data class ChoreSpacing(
    val xSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val xLarge: Dp = 32.dp,
)

private val MemberColorA = Color(0xFF2F5D50)
private val MemberColorB = Color(0xFFB15A44)
private val MemberColorC = Color(0xFFD9A441)
private val MemberColorD = Color(0xFF647C68)

// "Member colours = avatar colours = chart colours" (redesign plan) - one indexed palette so an
// avatar, a balance bar segment and a chart series for the same household member always agree.
@Immutable
data class MemberPalette(
    private val colors: List<Color> = listOf(MemberColorA, MemberColorB, MemberColorC, MemberColorD),
) {
    fun color(index: Int): Color = colors[index % colors.size]
}

val LocalSpacing = staticCompositionLocalOf { ChoreSpacing() }
val LocalMemberPalette = staticCompositionLocalOf { MemberPalette() }

private val WarmShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
private fun choreTypography(): Typography {
    val bricolage = ChoreFonts.bricolageGrotesque()
    val jakarta = ChoreFonts.plusJakartaSans()
    return remember(bricolage, jakarta) { buildTypography(bricolage, jakarta) }
}

private fun buildTypography(bricolage: FontFamily, jakarta: FontFamily) = Typography(
    displaySmall = TextStyle(
        fontFamily = bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = jakarta,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(fontFamily = jakarta, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = jakarta, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = jakarta, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = jakarta, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(
        fontFamily = jakarta,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = jakarta,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun ChoreTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = warmUtilityColorScheme(
        darkTheme = darkTheme,
        useDynamicColor = useDynamicColor,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = choreTypography(),
        shapes = WarmShapes,
    ) {
        CompositionLocalProvider(
            LocalSpacing provides ChoreSpacing(),
            LocalMemberPalette provides MemberPalette(),
            content = content,
        )
    }
}

@Composable
private fun warmUtilityColorScheme(
    darkTheme: Boolean,
    useDynamicColor: Boolean,
): ColorScheme {
    if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return if (darkTheme) WarmDarkColors else WarmLightColors
}
