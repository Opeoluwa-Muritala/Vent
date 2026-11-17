package app.vent.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val VentLightColors = lightColorScheme(
    primary = Color(0xFF006B65), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFA2F2E8), onPrimaryContainer = Color(0xFF00201E), inversePrimary = Color(0xFF7ED8CE),
    secondary = Color(0xFF416474), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFC5E9FC), onSecondaryContainer = Color(0xFF001F29),
    tertiary = Color(0xFF985000), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFFFDCC1), onTertiaryContainer = Color(0xFF311300),
    background = Color(0xFFFFF9F2), onBackground = Color(0xFF171D1B), surface = Color(0xFFFFF9F2), onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5E1), onSurfaceVariant = Color(0xFF3F4947), surfaceTint = Color(0xFF006B65), inverseSurface = Color(0xFF2C3230), inverseOnSurface = Color(0xFFEDF2EF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF9F3EC), surfaceContainer = Color(0xFFF3EDE6), surfaceContainerHigh = Color(0xFFEDE7E0), surfaceContainerHighest = Color(0xFFE7E1DA),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F7976), outlineVariant = Color(0xFFBEC9C6), scrim = Color(0xFF000000),
)

internal val VentDarkColors = darkColorScheme(
    primary = Color(0xFF7ED8CE), onPrimary = Color(0xFF003734), primaryContainer = Color(0xFF00504C), onPrimaryContainer = Color(0xFFA2F2E8), inversePrimary = Color(0xFF006B65),
    secondary = Color(0xFFA9CDE0), onSecondary = Color(0xFF0C3444), secondaryContainer = Color(0xFF294B5C), onSecondaryContainer = Color(0xFFC5E9FC),
    tertiary = Color(0xFFFFB870), onTertiary = Color(0xFF512400), tertiaryContainer = Color(0xFF743600), onTertiaryContainer = Color(0xFFFFDCC1),
    background = Color(0xFF071312), onBackground = Color(0xFFDDE5E2), surface = Color(0xFF071312), onSurface = Color(0xFFDDE5E2),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C6), surfaceTint = Color(0xFF7ED8CE), inverseSurface = Color(0xFFDDE5E2), inverseOnSurface = Color(0xFF28302E),
    surfaceContainerLowest = Color(0xFF020908), surfaceContainerLow = Color(0xFF0D1917), surfaceContainer = Color(0xFF111D1B), surfaceContainerHigh = Color(0xFF1B2725), surfaceContainerHighest = Color(0xFF26312F),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899390), outlineVariant = Color(0xFF3F4947), scrim = Color(0xFF000000),
)
