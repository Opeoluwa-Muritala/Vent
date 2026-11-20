package app.vent

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import app.vent.ui.theme.spacing
import app.vent.ui.theme.EventIcons
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import app.vent.ui.theme.SessionHeaderBackground
import app.vent.ui.theme.SessionHeaderForeground

@Composable
internal fun DefaultSessionHeader(modifier: Modifier) {
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    val foreground = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.background(background).drawBehind {
        for (index in 0..12) {
            val x = size.width * index / 9
            drawLine(foreground.copy(alpha = 0.09f), Offset(x, 0f), Offset(x - size.height, size.height))
        }
    }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Icon(painterResource(EventIcons.image), null, Modifier.size(MaterialTheme.spacing.xxl), tint = foreground)
            Text("Session image", color = foreground, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal expect fun rememberSessionImagePicker(onRead: (String?) -> Unit): (() -> Unit)?

@Composable
internal expect fun SessionHeaderImage(image: String?, modifier: Modifier)
