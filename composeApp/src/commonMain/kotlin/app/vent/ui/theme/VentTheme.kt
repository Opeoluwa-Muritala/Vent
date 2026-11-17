package app.vent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun VentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalVentSpacing provides VentSpacing()) {
        MaterialTheme(
            colorScheme = if (darkTheme) VentDarkColors else VentLightColors,
            typography = VentTypography,
            shapes = VentShapes,
            content = content,
        )
    }
}
