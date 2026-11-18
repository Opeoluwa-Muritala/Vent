package app.vent

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import app.vent.ui.theme.SessionHeaderBackground
import app.vent.ui.theme.SessionHeaderForeground

@Composable
internal fun DefaultSessionHeader(modifier: Modifier) {
    Box(modifier.background(SessionHeaderBackground).drawBehind {
        for (index in 0..12) {
            val x = size.width * index / 9
            drawLine(SessionHeaderForeground.copy(alpha = 0.09f), Offset(x, 0f), Offset(x - size.height, size.height))
        }
    })
}

@Composable
internal expect fun rememberSessionImagePicker(onRead: (String?) -> Unit): (() -> Unit)?

@Composable
internal expect fun SessionHeaderImage(image: String?, modifier: Modifier)
