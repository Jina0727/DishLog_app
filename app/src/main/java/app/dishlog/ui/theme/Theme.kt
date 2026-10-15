package app.dishlog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val Ground = Color(0xFFF5F5F2)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF161616)
    val Body = Color(0xFF3A3A36)
    val Muted = Color(0xFF5E5E58)
    val Faint = Color(0xFFA3A39C)
    val Line = Color(0xFFE6E6E1)
    val Border = Color(0xFFDCDCD6)
    val Placeholder = Color(0xFFE6E2D8)
    val Accent = Color(0xFF2F5D46)
    val AccentSoft = Color(0xFF8FA99A)
    val AccentDeep = Color(0xFF4E6F5E)
    val Camera = Color(0xFF0F0F0E)
}

private val scheme = lightColorScheme(
    primary = C.Accent,
    onPrimary = Color.White,
    background = C.Ground,
    onBackground = C.Ink,
    surface = C.Surface,
    onSurface = C.Ink,
    surfaceVariant = Color(0xFFEEEEEA),
    onSurfaceVariant = C.Muted,
    outline = C.Border,
    secondaryContainer = Color(0xFFE6E6E1),
    onSecondaryContainer = C.Ink,
)

@Composable
fun DishlogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
