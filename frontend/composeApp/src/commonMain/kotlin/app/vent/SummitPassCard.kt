package app.vent

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.vent.composeapp.generated.resources.Res
import app.vent.composeapp.generated.resources.demo_pass_code
import app.vent.ui.theme.EventIcons
import app.vent.ui.theme.VentTheme
import app.vent.ui.theme.passCodeSurface
import app.vent.ui.theme.spacing
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
internal fun SummitPassCard(
    eventTitle: String,
    attendee: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    demo: Boolean = false,
    token: String? = null,
) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(eventTitle, style = MaterialTheme.typography.titleLarge)
            Text(attendee, style = MaterialTheme.typography.headlineSmall)
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    "All-access",
                    Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            val codeSize = if (compact) spacing.huge * 3 else spacing.huge * 6
            if (token != null) {
                Surface(
                    Modifier.size(codeSize),
                    color = MaterialTheme.colorScheme.passCodeSurface,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Image(
                        rememberQrCodePainter(token),
                        "Verified event admission pass",
                        Modifier.fillMaxSize().padding(spacing.lg),
                    )
                }
            } else if (demo) {
                Image(
                    painterResource(Res.drawable.demo_pass_code),
                    "Demo pass code",
                    Modifier.size(codeSize),
                )
                Text("Demo pass", style = MaterialTheme.typography.labelLarge)
            } else {
                Surface(
                    Modifier.size(codeSize),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        Modifier.padding(spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(painterResource(EventIcons.cameraQr), null, Modifier.size(spacing.touch))
                        Text("Pass code not issued", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (!compact) {
                    Text(
                        "Your QR code will appear here when your pass is issued.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PassPendingLightPreview() {
    VentTheme(darkTheme = false) { SummitPassCard("TechCon Summit 2025", "Attendee") }
}

@Preview
@Composable
private fun PassPendingDarkPreview() {
    VentTheme(darkTheme = true) { SummitPassCard("TechCon Summit 2025", "Attendee") }
}
