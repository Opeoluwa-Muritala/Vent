package app.vent.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val SessionHeaderBackground = Color(0xFF10383A)
internal val SessionHeaderForeground = Color(0xFFFFFFFF)

internal val SummitLightColors by lazy {
    VentLightColors.copy(
        primary = Color(0xFF005052), onPrimary = Color.White,
        primaryContainer = Color(0xFFCCE6E6), onPrimaryContainer = Color(0xFF003738),
        secondaryContainer = Color(0xFFF3E4C7), onSecondaryContainer = Color(0xFF392B15),
        surface = Color(0xFFFAF8F1), background = Color(0xFFFAF8F1),
        surfaceContainerLowest = Color(0xFFFCFAF5),
        surfaceContainer = Color(0xFFE8F1F0), surfaceContainerLow = Color(0xFFEDF5F4),
        surfaceContainerHigh = Color(0xFFE2EBEA), onSurface = Color(0xFF101D1D),
    )
}

internal val VentLightColors = lightColorScheme(
    primary = Color(0xFF3B5B75), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFD0E4FA), onPrimaryContainer = Color(0xFF101D27), inversePrimary = Color(0xFFAACAE8),
    secondary = Color(0xFF53606C), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFD6E4F2), onSecondaryContainer = Color(0xFF101D27),
    tertiary = Color(0xFF655971), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFECDDF9), onTertiaryContainer = Color(0xFF21162C),
    background = Color(0xFFF7F9FB), onBackground = Color(0xFF181C20), surface = Color(0xFFF7F9FB), onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDFE3E8), onSurfaceVariant = Color(0xFF43474B), surfaceTint = Color(0xFF42627C), inverseSurface = Color(0xFF2D3135), inverseOnSurface = Color(0xFFEEF1F6),
    surfaceContainerLowest = Color(0xFFFCFAF5), surfaceContainerLow = Color(0xFFF1F4F7), surfaceContainer = Color(0xFFECEFF2), surfaceContainerHigh = Color(0xFFE6E9EC), surfaceContainerHighest = Color(0xFFE0E3E7),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF74787D), outlineVariant = Color(0xFFC3C7CC), scrim = Color(0xFF000000),
)

internal val VentDarkColors = darkColorScheme(
    primary = Color(0xFFAACAE8), onPrimary = Color(0xFF0B344B), primaryContainer = Color(0xFF294A63), onPrimaryContainer = Color(0xFFCBE6FF), inversePrimary = Color(0xFF3B5B75),
    secondary = Color(0xFFBBC8D6), onSecondary = Color(0xFF25323D), secondaryContainer = Color(0xFF3B4854), onSecondaryContainer = Color(0xFFD6E4F2),
    tertiary = Color(0xFFD0C1DD), onTertiary = Color(0xFF362B41), tertiaryContainer = Color(0xFF4D4259), onTertiaryContainer = Color(0xFFECDDF9),
    background = Color(0xFF101418), onBackground = Color(0xFFE0E3E7), surface = Color(0xFF101418), onSurface = Color(0xFFE0E3E7),
    surfaceVariant = Color(0xFF43474B), onSurfaceVariant = Color(0xFFC3C7CC), surfaceTint = Color(0xFFAACAE8), inverseSurface = Color(0xFFE0E3E7), inverseOnSurface = Color(0xFF2D3135),
    surfaceContainerLowest = Color(0xFF0B0F12), surfaceContainerLow = Color(0xFF181C20), surfaceContainer = Color(0xFF1C2024), surfaceContainerHigh = Color(0xFF272A2F), surfaceContainerHighest = Color(0xFF32353A),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8D9196), outlineVariant = Color(0xFF43474B), scrim = Color(0xFF000000),
)
