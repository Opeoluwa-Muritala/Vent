package app.vent

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import app.vent.composeapp.generated.resources.*
import app.vent.ui.theme.spacing
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.launch

@Composable
internal fun SessionsHome(ui: VentUiState, dispatch: (VentAction) -> Unit) {
    val pager = rememberPagerState { ui.eventDays }
    val scope = rememberCoroutineScope()
    val space = MaterialTheme.spacing
    val scheme = MaterialTheme.colorScheme
    val sessionColors = CardDefaults.cardColors(scheme.surface, scheme.onSurface)
    val sessionBorder = BorderStroke(space.xs / 4, scheme.outline)
    val sessionShadow = Modifier
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var createSession by rememberSaveable { mutableStateOf(false) }
    val organizer = ui.role == DemoRole.ORGANIZER

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = space.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (organizer) ui.eventTitle else "Today", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    IconButton({
                        searchOpen = !searchOpen
                        if (!searchOpen) search = ""
                    }) { Icon(if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search, if (searchOpen) "Close search" else "Search sessions") }
                    IconButton({ dispatch(VentAction.Navigate(VentScreen.PROFILE)) }) {
                        Surface(Modifier.size(space.huge), shape = androidx.compose.foundation.shape.CircleShape,
                            color = scheme.secondaryContainer) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, "Profile") }
                        }
                    }
                }
                if (searchOpen) {
                    OutlinedTextField(search, { search = it },
                        Modifier.fillMaxWidth(), placeholder = { Text("Search sessions") }, singleLine = true)
                }
            }
            VentPillTabs(List(ui.eventDays) { "Day ${it + 1}" }, pager.currentPage) { page ->
                scope.launch { pager.animateScrollToPage(page) }
            }
            HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Top) { page ->
                val sessions = (SummitSession.entries + ui.sessionDetails.keys).distinct().filter { session ->
                    val details = ui.sessionDetails[session] ?: SessionDetails(session.title, session.time, session.room, session.speaker)
                    details.day == page + 1 && listOf(details.title, details.speaker, details.room).any { it.contains(search, ignoreCase = true) }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = space.lg, end = space.lg, top = space.sm,
                        bottom = if (organizer) space.heroIcon + space.lg else space.sm),
                    verticalArrangement = Arrangement.spacedBy(space.md),
                ) {
                    if (sessions.isEmpty()) item { Text(if (search.isBlank()) "No sessions scheduled" else "No matching sessions") }
                    sessions.groupBy { sessionStartLabel(ui.sessionDetails[it]?.time ?: it.time) }.forEach { (time, concurrent) ->
                        item(key = "time:$time") { Text(time, style = MaterialTheme.typography.labelLarge) }
                        items(concurrent, key = { it.name }) { session ->
                            val details = ui.sessionDetails[session] ?: SessionDetails(session.title, session.time, session.room, session.speaker)
                            if (session == SummitSession.KEYNOTE) {
                                KeynoteCard(details, sessionColors, sessionBorder, sessionShadow) { dispatch(VentAction.OpenSession(session)) }
                            } else {
                                Card(
                                    onClick = { dispatch(VentAction.OpenSession(session)) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = space.heroIcon + space.lg),
                                    shape = MaterialTheme.shapes.large,
                                    colors = sessionColors,
                                    border = sessionBorder,
                                ) {
                                    Row(Modifier.fillMaxSize().padding(space.lg), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(space.md)) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.sm)) {
                                            Text(details.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                                                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            Text("${details.room} · ${details.speaker}", style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                        if (!organizer) IconButton({ dispatch(VentAction.Bookmark(details.title)) }) {
                                            Icon(if (details.title in ui.bookmarks) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                                "Save session", tint = if (details.title in ui.bookmarks) scheme.secondary else scheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (organizer) {
            ExtendedFloatingActionButton(
                onClick = { createSession = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(space.lg),
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("New session") },
            )
        }
    }
    if (createSession && organizer) {
        EditSessionDialog(SessionDetails("", "", "", "", day = pager.currentPage + 1), { createSession = false },
            creating = true, eventDays = ui.eventDays,
            halls = (SummitSession.entries + ui.sessionDetails.keys).map { ui.sessionDetails[it]?.room ?: it.room }.distinct()) {
            dispatch(VentAction.CreateSession(it))
            createSession = false
        }
    }
}

@Composable
internal fun VentPillTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val space = MaterialTheme.spacing
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().selectableGroup().horizontalScroll(rememberScrollState())
            .padding(horizontal = space.lg, vertical = space.xs),
        horizontalArrangement = Arrangement.spacedBy(space.xs),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val requester = remember { BringIntoViewRequester() }
            LaunchedEffect(selected) {
                if (selected) requester.bringIntoView()
            }
            Surface(
                Modifier.clip(MaterialTheme.shapes.extraLarge)
                    .bringIntoViewRequester(requester)
                    .selectable(selected, role = Role.Tab, onClick = { onSelect(index) })
                    .heightIn(min = space.touch),
                color = if (selected) scheme.secondary else scheme.surface,
                contentColor = if (selected) scheme.onSecondary else scheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Box(Modifier.padding(horizontal = space.lg), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
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
        shape = MaterialTheme.shapes.large,
        colors = colors,
        border = border,
    ) {
        Column(Modifier.padding(space.lg), verticalArrangement = Arrangement.spacedBy(space.md)) {
            Text("Featured keynote", style = MaterialTheme.typography.labelMedium)
            Text(details.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
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
                Text("View session")
                Spacer(Modifier.width(space.sm))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
            }
        }
    }
}

internal fun sessionStartLabel(time: String): String =
    time.trim().split(Regex("\\s*(?:[–—]|\\s-\\s)\\s*"), limit = 2).first().trim()
