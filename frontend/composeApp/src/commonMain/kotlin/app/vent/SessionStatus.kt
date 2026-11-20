package app.vent

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import app.vent.ui.theme.spacing

@Composable
internal fun SessionStatus(phase: SessionPhase, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (phase == SessionPhase.LIVE) scheme.tertiary else scheme.onSurfaceVariant
    Surface(modifier, shape = MaterialTheme.shapes.small, color = tint.copy(alpha = .12f)) {
        Row(Modifier.padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            Icon(when (phase) {
                SessionPhase.LIVE -> Icons.Outlined.Circle
                SessionPhase.ENDED -> Icons.Outlined.CheckCircle
                SessionPhase.UPCOMING -> Icons.Outlined.Schedule
            }, null, Modifier.size(MaterialTheme.spacing.xxl), tint = tint)
            Text(when (phase) {
                SessionPhase.LIVE -> "Live now"
                SessionPhase.ENDED -> "Ended"
                SessionPhase.UPCOMING -> "Upcoming"
            }, style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

@Composable
internal fun SessionScheduleCard(
    details: SessionDetails,
    featured: Boolean,
    organizer: Boolean,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onOpen: () -> Unit,
    onCheckIn: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val borderWidth = spacing.xs
    Card(onClick = onOpen,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (featured) spacing.huge * 4 + spacing.xl else spacing.touch + spacing.huge)
            .alpha(if (details.phase == SessionPhase.ENDED) .6f else 1f)
            .drawWithContent {
                drawContent()
                if (featured) drawLine(primary, Offset.Zero, Offset(0f, size.height), borderWidth.toPx())
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(spacing.xs / 4, if (details.phase == SessionPhase.LIVE) scheme.tertiary else scheme.outline)) {
        Column(Modifier.padding(if (organizer) spacing.md else spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(details.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (!organizer) IconButton(onBookmark) {
                    Icon(if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        if (bookmarked) "Remove bookmark" else "Save session",
                        tint = if (bookmarked) scheme.secondary else scheme.onSurfaceVariant)
                }
            }
            Text(details.time + " · " + details.room, style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant)
            Text(details.speaker, style = MaterialTheme.typography.bodyMedium)
            if (organizer || details.phase != SessionPhase.UPCOMING) SessionStatus(details.phase)
            if (!organizer && details.phase == SessionPhase.LIVE) {
                Button(onCheckIn, Modifier.fillMaxWidth().height(spacing.action)) { Text("Check in") }
            }
        }
    }
}
