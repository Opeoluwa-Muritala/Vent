package app.vent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun VentTheme(
    darkTheme: Boolean = true,
    accent: androidx.compose.ui.graphics.Color? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalVentSpacing provides VentSpacing()) {
        MaterialTheme(
            colorScheme = if (darkTheme) {
                if (accent == null) VentDarkColors else VentDarkColors.copy(primary = accent, surfaceTint = accent)
            } else VentLightColors,
            typography = VentTypography,
            shapes = VentShapes,
            content = content,
        )
    }
}
