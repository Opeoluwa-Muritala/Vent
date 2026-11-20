package app.vent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import app.vent.domain.EventPalette

@Composable
fun VentTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    accent: androidx.compose.ui.graphics.Color? = null,
    content: @Composable () -> Unit,
) {
    val accents = EventPalette.accents(accent?.toArgb(), darkTheme)
    val base = if (darkTheme) VentDarkColors else VentLightColors
    val palette = base.copy(
        primary = Color(accents.primary), onPrimary = Color(accents.onPrimary),
        primaryContainer = lerp(base.surface, Color(accents.primary), .18f),
        onPrimaryContainer = base.onSurface,
        secondary = Color(accents.secondary), onSecondary = Color(accents.onSecondary),
        secondaryContainer = lerp(base.surface, Color(accents.secondary), .18f),
        onSecondaryContainer = base.onSurface,
        tertiary = Color(accents.tertiary), surfaceTint = Color(accents.primary),
    )
    CompositionLocalProvider(LocalVentSpacing provides VentSpacing()) {
        MaterialTheme(
            colorScheme = palette,
            typography = VentTypography,
            shapes = VentShapes,
            content = content,
        )
    }
}
