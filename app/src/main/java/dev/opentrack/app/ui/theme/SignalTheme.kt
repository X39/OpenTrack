package dev.opentrack.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

object SignalPalette {
    val Ink = Color(0xFF191B1F)
    val Paper = Color(0xFFF6F4EE)
    val Surface = Color(0xFFFFFDF8)
    val Indigo = Color(0xFF5B5CE2)
    val IndigoSoft = Color(0xFFE7E7FF)
    val Moss = Color(0xFF237A57)
    val MossSoft = Color(0xFFD8F3E6)
    val Coral = Color(0xFFFF6B57)
    val Sun = Color(0xFFF4B942)
    val Sky = Color(0xFF5278E8)
    val Lilac = Color(0xFF8A65D6)
    val Rose = Color(0xFFD85F86)
    val Muted = Color(0xFF6F716F)
    val Line = Color(0xFFE4E1D9)
    val Night = Color(0xFF111315)
    val NightSurface = Color(0xFF1A1D20)
    val NightRaised = Color(0xFF22262A)
    val NightLine = Color(0xFF383D42)
}

private val LightColors = lightColorScheme(
    primary = SignalPalette.Indigo,
    onPrimary = Color.White,
    primaryContainer = SignalPalette.IndigoSoft,
    onPrimaryContainer = Color(0xFF252572),
    secondary = SignalPalette.Moss,
    tertiary = SignalPalette.Coral,
    background = SignalPalette.Paper,
    onBackground = SignalPalette.Ink,
    surface = SignalPalette.Surface,
    onSurface = SignalPalette.Ink,
    surfaceVariant = Color(0xFFEDEAE2),
    onSurfaceVariant = SignalPalette.Muted,
    outline = Color(0xFF777A76),
    outlineVariant = SignalPalette.Line,
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC2C1FF),
    onPrimary = Color(0xFF29287D),
    primaryContainer = Color(0xFF4242B5),
    onPrimaryContainer = Color(0xFFE3E1FF),
    secondary = Color(0xFF78D6AD),
    tertiary = Color(0xFFFFB4A8),
    background = SignalPalette.Night,
    onBackground = Color(0xFFE8E6E0),
    surface = SignalPalette.NightSurface,
    onSurface = Color(0xFFE8E6E0),
    surfaceVariant = SignalPalette.NightRaised,
    onSurfaceVariant = Color(0xFFBEC2BE),
    outline = Color(0xFF919792),
    outlineVariant = SignalPalette.NightLine,
    error = Color(0xFFFFB4AB),
)

private val SignalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SignalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = SignalTypography,
        shapes = SignalShapes,
        content = content,
    )
}
