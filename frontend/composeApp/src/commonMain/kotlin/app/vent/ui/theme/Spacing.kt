package app.vent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class VentSpacing(
    val none: Dp = 0.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    val huge: Dp = 40.dp,
    val giant: Dp = 48.dp,
    val touch: Dp = 48.dp,
    val action: Dp = 56.dp,
    val heroIcon: Dp = 72.dp,
    val contentMax: Dp = 1200.dp,
    val compactBreakpoint: Dp = 600.dp,
    val detailBreakpoint: Dp = 720.dp,
    val expandedBreakpoint: Dp = 840.dp,
)

internal val LocalVentSpacing = staticCompositionLocalOf { VentSpacing() }

val MaterialTheme.spacing: VentSpacing
    @Composable @ReadOnlyComposable
    get() = LocalVentSpacing.current
