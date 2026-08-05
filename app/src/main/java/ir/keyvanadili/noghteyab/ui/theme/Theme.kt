package ir.keyvanadili.noghteyab.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/** Shared rounded-rectangle shape used for every button in the app (no pill/stadium shapes). */
val AppButtonShape = RoundedCornerShape(14.dp)

private val AppColors = darkColorScheme(
    primary = TealAccent,
    onPrimary = AppBackground,
    secondary = TealAccentDark,
    onSecondary = AppBackground,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = AppSurface,
    onSurface = TextPrimary,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = DangerRed
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * App is always shown in this black/teal dark theme regardless of system setting,
 * to match the intended visual identity.
 */
@Composable
fun NoghteYabTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = AppColors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content
        )
    }
}
