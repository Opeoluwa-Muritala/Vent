package app.vent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.lerp

@Composable
fun VentTheme(
    darkTheme: Boolean = true,
    accent: androidx.compose.ui.graphics.Color? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalVentSpacing provides VentSpacing()) {
        MaterialTheme(
            colorScheme = if (darkTheme) {
                if (accent == null) VentDarkColors else VentDarkColors.copy(
                    primary = accent,
                    primaryContainer = lerp(VentDarkColors.surface, accent, .28f),
                    surfaceContainer = lerp(VentDarkColors.surfaceContainer, accent, .08f),
                    surfaceContainerHigh = lerp(VentDarkColors.surfaceContainerHigh, accent, .12f),
                    surfaceContainerHighest = lerp(VentDarkColors.surfaceContainerHighest, accent, .16f),
                    surfaceTint = accent,
                )
            } else VentLightColors,
            typography = VentTypography,
            shapes = VentShapes,
            content = content,
        )
    }
}
