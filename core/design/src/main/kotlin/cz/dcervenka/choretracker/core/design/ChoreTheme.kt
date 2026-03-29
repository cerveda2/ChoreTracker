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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WarmLightColors = lightColorScheme(
    primary = Color(0xFF2F5D50),
    onPrimary = Color(0xFFF8F4ED),
    primaryContainer = Color(0xFFDCEBDE),
    onPrimaryContainer = Color(0xFF143229),
    secondary = Color(0xFFB15A44),
    onSecondary = Color(0xFFFDF6F1),
    tertiary = Color(0xFFD9A441),
    onTertiary = Color(0xFF2F1D00),
    background = Color(0xFFF6F0E7),
    onBackground = Color(0xFF201B18),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF201B18),
    surfaceVariant = Color(0xFFE8DDD1),
    onSurfaceVariant = Color(0xFF51453C),
    outline = Color(0xFF8A7C72),
    error = Color(0xFFBA1A1A),
)

private val WarmDarkColors = darkColorScheme(
    primary = Color(0xFFA8D0BD),
    onPrimary = Color(0xFF113228),
    primaryContainer = Color(0xFF24483C),
    onPrimaryContainer = Color(0xFFC3ECD7),
    secondary = Color(0xFFFFB59F),
    onSecondary = Color(0xFF5F1C0A),
    background = Color(0xFF171311),
    onBackground = Color(0xFFECE1D7),
    surface = Color(0xFF171311),
    onSurface = Color(0xFFECE1D7),
    surfaceVariant = Color(0xFF51453C),
    onSurfaceVariant = Color(0xFFD4C4B8),
    outline = Color(0xFF9C8D82),
    tertiary = Color(0xFFF2C96B),
    onTertiary = Color(0xFF3D2C00),
)

@Immutable
data class ChoreSpacing(
    val xSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val xLarge: Dp = 32.dp,
)

@Immutable
data class StatsPalette(
    val chartA: Color = Color(0xFF2F5D50),
    val chartB: Color = Color(0xFFB15A44),
    val chartC: Color = Color(0xFFD9A441),
    val chartD: Color = Color(0xFF647C68),
)

val LocalSpacing = staticCompositionLocalOf { ChoreSpacing() }
val LocalStatsPalette = staticCompositionLocalOf { StatsPalette() }

private val WarmTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

private val WarmShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
        typography = WarmTypography,
        shapes = WarmShapes,
    ) {
        CompositionLocalProvider(
            LocalSpacing provides ChoreSpacing(),
            LocalStatsPalette provides StatsPalette(),
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
