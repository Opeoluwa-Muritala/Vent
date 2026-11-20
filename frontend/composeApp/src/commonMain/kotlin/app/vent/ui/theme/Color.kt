package app.vent.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val SessionHeaderBackground = Color(0xFF14161A)
internal val SessionHeaderForeground = Color(0xFFEEF0F4)

internal val VentLightColors = lightColorScheme(
    primary = Color(0xFF5B3FD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9E2FF),
    onPrimaryContainer = Color(0xFF1A1334),
    secondary = Color(0xFF1F6B5A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6EEE5),
    onSecondaryContainer = Color(0xFF0D2A23),
    tertiary = Color(0xFF8A5A12),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE5C5),
    onTertiaryContainer = Color(0xFF352008),
    background = Color(0xFFF7F7F9),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF4A4F5A),
    surfaceContainerLowest = Color(0xFFF7F7F9),
    surfaceContainerLow = Color(0xFFF7F7F9),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEFF3),
    surfaceContainerHighest = Color(0xFFE4E7ED),
    outline = Color(0xFFC5C9D2),
    outlineVariant = Color(0xFFC5C9D2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    surfaceTint = Color(0xFF5B3FD6),
)

internal val VentDarkColors = darkColorScheme(
    primary = Color(0xFFB9A7FF),
    onPrimary = Color(0xFF1A1334),
    primaryContainer = Color(0xFF342C4F),
    onPrimaryContainer = Color(0xFFEEF0F4),
    secondary = Color(0xFF9ED8C6),
    onSecondary = Color(0xFF0D2A23),
    secondaryContainer = Color(0xFF263D37),
    onSecondaryContainer = Color(0xFF9ED8C6),
    tertiary = Color(0xFFF2B872),
    onTertiary = Color(0xFF352008),
    tertiaryContainer = Color(0xFF453524),
    onTertiaryContainer = Color(0xFFF2B872),
    background = Color(0xFF14161A),
    onBackground = Color(0xFFEEF0F4),
    surface = Color(0xFF1D2026),
    onSurface = Color(0xFFEEF0F4),
    surfaceVariant = Color(0xFF272B33),
    onSurfaceVariant = Color(0xFFB8BDC9),
    surfaceContainerLowest = Color(0xFF14161A),
    surfaceContainerLow = Color(0xFF14161A),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF272B33),
    surfaceContainerHighest = Color(0xFF30353E),
    outline = Color(0xFF4A4F5A),
    outlineVariant = Color(0xFF4A4F5A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0906),
    errorContainer = Color(0xFF5B2420),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFFB9A7FF),
)

internal val ColorScheme.success: Color
    get() = if (background == VentDarkColors.background) Color(0xFF8FDB9B) else Color(0xFF1E7A34)

internal val ColorScheme.heroTint: Color
    get() = if (background == VentDarkColors.background) Color(0xFF241F28) else Color(0xFFF0E8ED)

internal val ColorScheme.passCodeSurface: Color
    get() = Color.White
