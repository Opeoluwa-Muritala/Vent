package app.vent

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vent.architecture.MviViewModel
import app.vent.composeapp.generated.resources.*
import app.vent.ui.theme.spacing
import org.jetbrains.compose.resources.painterResource

private val talks = SummitSession.entries.filter { it != SummitSession.KEYNOTE }
private data class Agenda(val day: Int = 1, val track: String = "All Tracks", val search: String = "", val saved: Set<String> = emptySet())
private sealed interface AgendaIntent {
    data class Day(val day: Int) : AgendaIntent
    data class Track(val track: String) : AgendaIntent
    data class Search(val query: String) : AgendaIntent
    data class Bookmark(val title: String) : AgendaIntent
}
private class SessionsModel : MviViewModel<Agenda, AgendaIntent, Nothing>(Agenda()) {
    override fun onIntent(intent: AgendaIntent) = reduce {
        when (intent) {
            is AgendaIntent.Day -> copy(day = intent.day)
            is AgendaIntent.Track -> copy(track = intent.track)
            is AgendaIntent.Search -> copy(search = intent.query)
            is AgendaIntent.Bookmark -> copy(saved = if (intent.title in saved) saved - intent.title else saved + intent.title)
        }
    }
}

@Composable
internal fun SessionsHome(ui: VentUiState, dispatch: (VentAction) -> Unit) {
    val model = viewModel { SessionsModel() }
    val state by model.uiState.collectAsState()
    val space = MaterialTheme.spacing
    val scheme = MaterialTheme.colorScheme
    val sessionColors = CardDefaults.cardColors(scheme.primaryContainer, scheme.onPrimaryContainer)
    val sessionBorder = BorderStroke(1.dp, scheme.primary.copy(alpha = .55f))
    val sessionShadow = Modifier.shadow(
        4.dp, MaterialTheme.shapes.extraLarge,
        ambientColor = scheme.primary, spotColor = scheme.primary,
    )
    var searchOpen by remember { mutableStateOf(false) }
    val sessions = (SummitSession.entries + ui.sessionDetails.keys).distinct().filter { session ->
        val details = ui.sessionDetails[session] ?: SessionDetails(session.title, session.time, session.room, session.speaker)
        details.day == state.day && listOf(details.title, details.speaker, details.room).any { it.contains(state.search, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().conferencePattern().safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ui.eventTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                IconButton({
                    searchOpen = !searchOpen
                    if (!searchOpen) model.onIntent(AgendaIntent.Search(""))
                }) { Icon(if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search, if (searchOpen) "Close search" else "Search sessions") }
                IconButton({ dispatch(VentAction.Navigate(VentScreen.PROFILE)) }) {
                    Image(painterResource(Res.drawable.pass_portrait), "Profile",
                        Modifier.size(space.xxl).clip(androidx.compose.foundation.shape.CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
            }
            if (searchOpen) {
                OutlinedTextField(state.search, { model.onIntent(AgendaIntent.Search(it)) },
                    Modifier.fillMaxWidth(), placeholder = { Text("Search sessions") }, singleLine = true)
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = space.lg, vertical = space.sm),
            verticalArrangement = Arrangement.spacedBy(space.md),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                ) {
                    listOf(1, 2).forEach { day ->
                        FilterChip(
                            selected = state.day == day,
                            onClick = { model.onIntent(AgendaIntent.Day(day)) },
                            label = { Text("Day $day · Oct ${23 + day}") },
                            leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(FilterChipDefaults.IconSize)) },
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = scheme.surfaceContainerHighest,
                                selectedContainerColor = scheme.primaryContainer,
                                selectedLabelColor = scheme.onPrimaryContainer,
                                selectedLeadingIconColor = scheme.onPrimaryContainer,
                            ),
                            border = null,
                        )
                    }
                }
            }
            if (sessions.isEmpty()) item { Text(if (state.search.isBlank()) "No sessions scheduled" else "No matching sessions") }
            sessions.groupBy { sessionStartLabel(ui.sessionDetails[it]?.time ?: it.time) }.forEach { (time, concurrent) ->
                item(key = "time:$time") { Text(time, style = MaterialTheme.typography.labelLarge) }
                items(concurrent, key = { it.name }) { session ->
                    val details = ui.sessionDetails[session] ?: SessionDetails(session.title, session.time, session.room, session.speaker)
                    if (session == SummitSession.KEYNOTE) {
                        KeynoteCard(details, sessionColors, sessionBorder, sessionShadow) { dispatch(VentAction.OpenSession(session)) }
                    } else {
                        Card(
                            onClick = { dispatch(VentAction.OpenSession(session)) },
                            modifier = Modifier.fillMaxWidth().height(space.heroIcon + space.xxxl).then(sessionShadow),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = sessionColors,
                            border = sessionBorder,
                        ) {
                            Row(Modifier.fillMaxSize().padding(space.lg), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(space.md)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.sm)) {
                                    Text(details.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text("${details.room} · ${details.speaker}", style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeynoteCard(details: SessionDetails, colors: CardColors, border: BorderStroke, shadow: Modifier, onOpen: () -> Unit) {
    val space = MaterialTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth().then(shadow),
        shape = MaterialTheme.shapes.extraLarge,
        colors = colors,
        border = border,
    ) {
        Column(Modifier.padding(space.lg), verticalArrangement = Arrangement.spacedBy(space.md)) {
            Text("Featured keynote", style = MaterialTheme.typography.labelMedium)
            Text(details.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(FilterChipDefaults.IconSize))
                Text(details.time, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Icon(Icons.Outlined.LocationOn, null, Modifier.size(FilterChipDefaults.IconSize))
                Text(details.room, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                Image(
                    painterResource(Res.drawable.keynote_speakers), null,
                    Modifier.width(space.action).height(space.huge).clip(MaterialTheme.shapes.large),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                Text(details.speaker, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onOpen, Modifier.fillMaxWidth().heightIn(min = space.action)) {
                Text("View Details & Check In")
                Spacer(Modifier.width(space.sm))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
            }
        }
    }
}

internal fun sessionStartLabel(time: String): String =
    time.trim().split(Regex("\\s*(?:[–—]|\\s-\\s)\\s*"), limit = 2).first().trim()
