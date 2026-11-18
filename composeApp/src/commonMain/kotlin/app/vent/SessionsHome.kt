package app.vent

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
    var searchOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var sortByTitle by remember { mutableStateOf(false) }
    val sessions = if (state.day == 1) SummitSession.entries.filter { session ->
        val details = ui.sessionDetails[session] ?: SessionDetails(session.title, session.time, session.room, session.speaker)
        listOf(details.title, details.speaker, details.room).any { it.contains(state.search, ignoreCase = true) }
    } else emptyList()
    val ordered = if (sortByTitle) sessions.sortedBy { ui.sessionDetails[it]?.title ?: it.title } else sessions

    LazyColumn(
        Modifier.fillMaxSize().conferencePattern().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = space.lg, vertical = space.sm),
        verticalArrangement = Arrangement.spacedBy(space.md),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (searchOpen) {
                    OutlinedTextField(state.search, { model.onIntent(AgendaIntent.Search(it)) },
                        Modifier.weight(1f), placeholder = { Text("Search sessions") }, singleLine = true)
                } else {
                    Text(ui.eventTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                }
                IconButton({
                    searchOpen = !searchOpen
                    if (!searchOpen) model.onIntent(AgendaIntent.Search(""))
                }) { Icon(if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search, if (searchOpen) "Close search" else "Search sessions") }
                Box {
                    IconButton({ sortOpen = true }) { Icon(Icons.Outlined.Sort, "Sort sessions") }
                    DropdownMenu(sortOpen, { sortOpen = false }) {
                        DropdownMenuItem(text = { Text("Schedule order") }, onClick = { sortByTitle = false; sortOpen = false })
                        DropdownMenuItem(text = { Text("Title A–Z") }, onClick = { sortByTitle = true; sortOpen = false })
                    }
                }
                IconButton({ dispatch(VentAction.Navigate(VentScreen.PROFILE)) }) {
                    Image(painterResource(Res.drawable.pass_portrait), "Profile",
                        Modifier.size(space.xxl).clip(androidx.compose.foundation.shape.CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                listOf(1, 2).forEach { day ->
                    FilterChip(state.day == day, { model.onIntent(AgendaIntent.Day(day)) },
                        { Text("Day ${day} · Oct ${23 + day}") }, shape = MaterialTheme.shapes.extraLarge)
                }
            }
        }
        if (ordered.isEmpty()) item { Text(if (state.search.isBlank()) "No sessions scheduled" else "No matching sessions") }
        items(ordered, key = { it.name }) { session ->
            val details = ui.sessionDetails[session] ?: SessionDetails(session.title, session.time, session.room, session.speaker)
            val scheme = MaterialTheme.colorScheme
            val colors = when (session) {
                SummitSession.SECURITY -> CardDefaults.cardColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
                SummitSession.TOOLING -> CardDefaults.cardColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
                else -> CardDefaults.cardColors(scheme.primaryContainer, scheme.onPrimaryContainer)
            }
            Card(
                onClick = { dispatch(VentAction.OpenSession(session)) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = colors,
            ) {
                Row(Modifier.padding(space.lg), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.md)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.sm)) {
                        Text(details.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(details.room, style = MaterialTheme.typography.bodyMedium)
                        if (session == SummitSession.KEYNOTE) {
                            Image(painterResource(Res.drawable.keynote_speakers), session.speaker,
                                Modifier.width(space.action).height(space.huge).clip(MaterialTheme.shapes.large),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        } else {
                            Text(details.speaker, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}
