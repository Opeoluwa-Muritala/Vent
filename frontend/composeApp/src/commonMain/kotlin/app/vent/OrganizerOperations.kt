package app.vent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import app.vent.composeapp.generated.resources.Res
import app.vent.composeapp.generated.resources.conference_stage
import app.vent.ui.theme.VentTheme
import app.vent.ui.theme.spacing
import app.vent.ui.theme.success
import androidx.compose.ui.draw.alpha
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
internal fun OrganizerOperations(
    screen: VentScreen,
    ui: VentUiState,
    dispatch: (VentAction) -> Unit,
    onSignOut: (() -> Unit)? = null,
) {
    if (ui.role != DemoRole.ORGANIZER) return
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        IconButton({ dispatch(VentAction.Navigate(VentScreen.ORGANIZER)) }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to events")
        }
        if (screen == VentScreen.SETUP) {
            val pager = rememberPagerState { 2 }
            val scope = rememberCoroutineScope()
            VentPillTabs(listOf("Event setup", "Live controls"), pager.currentPage) { page ->
                scope.launch { pager.animateScrollToPage(page) }
            }
            HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Top) { page ->
                OperationsPage(Modifier.fillMaxSize()) {
                    if (page == 0) SetupOperations(ui, dispatch, onSignOut)
                    else LiveOperations(ui, dispatch)
                }
            }
        } else {
            OperationsPage(Modifier.fillMaxWidth().weight(1f)) {
                when (screen) {
                    VentScreen.DOOR -> DoorOperations(ui, dispatch)
                    VentScreen.OCCUPANCY -> OccupancyOperations(ui, dispatch)
                    else -> LiveOperations(ui, dispatch)
                }
            }
        }
    }
}

@Composable
private fun OperationsPage(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val space = MaterialTheme.spacing
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = space.detailBreakpoint).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(space.lg),
            verticalArrangement = Arrangement.spacedBy(space.lg),
            content = content,
        )
    }
}

@Composable
private fun OpsCard(content: @Composable ColumnScope.() -> Unit) {
    val space = MaterialTheme.spacing
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(space.lg), verticalArrangement = Arrangement.spacedBy(space.md), content = content)
    }
}

@Composable
private fun OpsBadge(label: String, emphasized: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val space = MaterialTheme.spacing
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (emphasized) scheme.primary else scheme.primaryContainer,
        contentColor = if (emphasized) scheme.onPrimary else scheme.onPrimaryContainer,
    ) {
        Text(label, Modifier.padding(horizontal = space.sm, vertical = space.xs), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OpsHeading(icon: ImageVector, title: String, detail: String? = null) {
    val space = MaterialTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        detail?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun DoorOperations(ui: VentUiState, dispatch: (VentAction) -> Unit) {
    val space = MaterialTheme.spacing
    var showGates by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var scanReceived by rememberSaveable { mutableStateOf(false) }
    OpsCard {
        OpsHeading(Icons.Outlined.MeetingRoom, "Current gate")
        Text("${ui.session.room} · ${ui.activeGate}", style = MaterialTheme.typography.titleLarge)
    }
    Surface(Modifier.fillMaxWidth().aspectRatio(1.4f), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
        if (scanReceived) {
            Column(Modifier.padding(space.lg), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Code read. Staff verification unavailable.")
                TextButton({ scanReceived = false }) { Text("Scan another code") }
            }
        } else if (LocalInspectionMode.current) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Align QR code") }
        } else {
            CheckInCamera(Modifier.fillMaxSize()) { scanReceived = true }
        }
    }
    OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("Search registered attendees") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
    if (search.isNotBlank()) {
        val matches = ui.attendees.filter { it.contains(search.trim(), ignoreCase = true) }.sorted().take(10)
        OpsCard {
            if (matches.isEmpty()) Text("No matching attendees")
            matches.forEach { Text(it) }
        }
    }
    OpsHeading(Icons.Outlined.History, "Recent Door Scans")
    OpsCard { Text("No staff scan history is available.") }
    OutlinedButton({ showGates = true }, Modifier.fillMaxWidth()) { Text("Switch gate") }
    if (showGates) AlertDialog(
        onDismissRequest = { showGates = false },
        title = { Text("Select gate") },
        text = {
            Column {
                listOf("West Door 2", "East Door 1").forEach { gate ->
                    TextButton({
                        dispatch(VentAction.SelectGate(gate))
                        showGates = false
                    }) { Text(gate) }
                }
            }
        },
        confirmButton = { TextButton({ showGates = false }) { Text("Cancel") } },
    )
}

@Composable
private fun OccupancyOperations(ui: VentUiState, dispatch: (VentAction) -> Unit) {
    val space = MaterialTheme.spacing
    val sessions = (SummitSession.entries + ui.sessionDetails.keys).distinct()
    fun hall(session: SummitSession): String = ui.sessionDetails[session]?.room ?: session.room
    val halls = sessions.groupBy(::hall)
    var activeHall by rememberSaveable { mutableStateOf(hall(ui.session)) }
    val hallSessions = halls[activeHall] ?: listOf(ui.session)
    var selectedName by rememberSaveable { mutableStateOf(ui.session.name) }
    val selected = hallSessions.firstOrNull { it.name == selectedName } ?: hallSessions.first()
    val rows = if (selected == ui.session) ui.venueRows else ui.seating[selected]?.rows ?: initialVenueRows()
    val assignedLabels = ui.seatAssignments[selected].orEmpty().values.toSet()
    val layoutSeats = rows.flatMap { it.seats }
    val capacity = layoutSeats.count { it.availability != SeatAvailability.BLOCKED }
    val assigned = layoutSeats.count { it.label in assignedLabels && it.availability != SeatAvailability.BLOCKED }
    val ratio = if (capacity == 0) 0f else (assigned.toFloat() / capacity).coerceIn(0f, 1f)
    OpsCard {
        OpsHeading(Icons.Outlined.EventSeat, ui.sessionDetails[selected]?.title ?: selected.title, hall(selected))
        Text("Room assignments", style = MaterialTheme.typography.titleSmall)
        LinearProgressIndicator({ ratio }, Modifier.fillMaxWidth())
        Text("$assigned of $capacity assigned", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            OccupancyMetric("Usable seats", capacity, Modifier.weight(1f))
            OccupancyMetric("Assigned seats", assigned, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            OccupancyMetric("Unassigned seats", capacity - assigned, Modifier.weight(1f))
            OccupancyMetric("Blocked seats", layoutSeats.size - capacity, Modifier.weight(1f))
        }
    }
    OpsHeading(Icons.Outlined.BarChart, "Hall Overview")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
        halls.keys.forEach { room ->
            FilterChip(room == hall(selected), { activeHall = room }, { Text(room) })
        }
    }
    if (hallSessions.size > 1) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            hallSessions.forEach { session ->
                FilterChip(session == selected, { selectedName = session.name }, {
                    Text(ui.sessionDetails[session]?.title ?: session.title, maxLines = 1)
                })
            }
        }
    }
    OpsCard {
        OpsHeading(Icons.Outlined.Chair, "Spatial Distribution")
        val seatColor = MaterialTheme.colorScheme.primary
        val availableColor = MaterialTheme.colorScheme.secondaryContainer
        val blockedColor = MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.fillMaxWidth().height(space.heroIcon * 2).alpha(.6f)) {
            rows.forEachIndexed { row, seats ->
                seats.seats.forEachIndexed { index, seat ->
                    val x = size.width * (index + 1) / (seats.seats.size + 1)
                    val y = size.height * (row + 1) / (rows.size + 1)
                    drawCircle(
                        when {
                            seat.availability == SeatAvailability.BLOCKED -> blockedColor
                            seat.label in assignedLabels -> seatColor
                            else -> availableColor
                        },
                        radius = size.minDimension / 55f,
                        center = Offset(x, y),
                    )
                }
            }
        }
        OutlinedButton({
            dispatch(VentAction.OpenSession(selected))
            dispatch(VentAction.Navigate(VentScreen.SEAT))
        }, Modifier.fillMaxWidth()) { Text("View event map") }
    }
    OpsCard {
        OpsHeading(Icons.Outlined.AssignmentTurnedIn, "End of Session Actions")
        Button({ dispatch(VentAction.Navigate(VentScreen.WRAPPED)) }, Modifier.fillMaxWidth()) { Text("View event recap") }
    }
}

@Composable
private fun OccupancyMetric(label: String, count: Int, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(MaterialTheme.spacing.md)) {
            if (label == "Blocked seats" && count > 0) Icon(Icons.Outlined.WarningAmber, "Blocked seats",
                tint = MaterialTheme.colorScheme.error)
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall,
                color = if (label == "Blocked seats" && count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun LiveOperations(ui: VentUiState, dispatch: (VentAction) -> Unit) {
    val space = MaterialTheme.spacing
    var demo by rememberSaveable { mutableStateOf(false) }
    var create by rememberSaveable { mutableStateOf(false) }
    var ending by rememberSaveable { mutableStateOf<String?>(null) }
    val poll = ui.questDefinitions.lastOrNull { it.kind == "Poll" && it.phase == QuestPhase.LIVE }
    OpsCard {
        OpsHeading(Icons.Outlined.Tune, ui.sessionDetails[ui.session]?.title ?: ui.session.title)
        Text("${ui.session.room} · ${ui.session.time}", style = MaterialTheme.typography.labelLarge)
    }
    if (poll == null) {
        OpsCard {
            Icon(Icons.Outlined.HowToVote, null, Modifier.size(space.heroIcon))
            Text("No live poll", style = MaterialTheme.typography.headlineSmall)
            Text("Publish a poll quest to collect attendee votes.")
            Button({ create = true }, Modifier.fillMaxWidth().height(space.action)) { Text("Create poll") }
        }
    } else OpsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            val statusColor = if (ui.pollPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.success
            Icon(if (ui.pollPaused) Icons.Outlined.Pause else Icons.Outlined.Circle, null, tint = statusColor)
            Text(if (ui.pollPaused) "Paused" else "Live", color = statusColor)
        }
        Text(poll.prompt, style = MaterialTheme.typography.titleLarge)
        val answers = ui.questResponses.values.mapNotNull { it[poll.title] }
        poll.options.forEach { option ->
            val count = answers.count { it == option }
            val share = if (answers.isEmpty()) 0f else count.toFloat() / answers.size
            Row {
                Text(option, Modifier.weight(1f))
                Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            }
            LinearProgressIndicator({ share }, Modifier.fillMaxWidth().height(space.xxxl))
        }
        Text("${answers.size} votes", style = MaterialTheme.typography.labelLarge)
        if (ui.pollPaused) Button({ dispatch(VentAction.TogglePoll) }, Modifier.fillMaxWidth().height(space.action)) {
            Text("Resume voting")
        } else OutlinedButton({ dispatch(VentAction.TogglePoll) }, Modifier.fillMaxWidth().height(space.action),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)) {
            Icon(Icons.Outlined.Pause, null)
            Text("Pause voting")
        }
        TextButton({ ending = poll.title }) { Text("End poll", color = MaterialTheme.colorScheme.error) }
    }
    if (!demo) OutlinedButton({ demo = true }, Modifier.fillMaxWidth()) { Text("Start demo") }
    else OpsCard {
        Text("Demo mode. Votes are not saved.", style = MaterialTheme.typography.labelLarge)
        OpsBadge("Demo", emphasized = false)
        Text("Which engineering priority matters most?", style = MaterialTheme.typography.titleLarge)
        listOf("Agentic workflows" to .42f, "On-device models" to .28f,
            "Zero-trust infrastructure" to .18f, "Developer tooling" to .12f).forEach { (label, share) ->
            Row { Text(label, Modifier.weight(1f)); Text("${(share * 100).toInt()}%") }
            LinearProgressIndicator({ share }, Modifier.fillMaxWidth().height(space.xxxl))
        }
        Button({ demo = false }, Modifier.fillMaxWidth().height(space.action)) { Text("Stop demo") }
    }
    if (create) CreateQuestDialog({ create = false }, initialKind = "Poll") {
        dispatch(VentAction.PublishQuest(it)); create = false
    }
    ending?.let { title ->
        AlertDialog(onDismissRequest = { ending = null }, title = { Text("End poll?") },
            text = { Text("Voting closes for all attendees. Existing results are kept.") },
            confirmButton = { TextButton({
                dispatch(VentAction.SetQuestPhase(title, QuestPhase.ENDED)); ending = null
            }) { Text("End poll", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ ending = null }) { Text("Cancel") } })
    }
}

@Composable
private fun SetupOperations(ui: VentUiState, dispatch: (VentAction) -> Unit, onSignOut: (() -> Unit)?) {
    val space = MaterialTheme.spacing
    var title by rememberSaveable(ui.eventTitle) { mutableStateOf(ui.eventTitle) }
    var previewSeed by rememberSaveable(ui.eventAccent) { mutableStateOf(ui.eventAccent) }
    var previewReady by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(previewSeed) {
        previewReady = false
        kotlinx.coroutines.delay(2_000)
        previewReady = true
    }
    VentTheme(accent = previewSeed?.let { Color(it) }) {
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
    OpsCard {
        OpsHeading(Icons.Outlined.Palette, "Event setup")
        Text("Set the event title and preview the event palette.", style = MaterialTheme.typography.bodyMedium)
    }
    OpsCard {
        OpsHeading(Icons.Outlined.CalendarMonth, "Event details")
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Event title") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Event days", Modifier.weight(1f))
            IconButton({ dispatch(VentAction.SetEventDays(ui.eventDays - 1)) }, enabled = ui.eventDays > 1) {
                Icon(Icons.Outlined.Remove, "Remove event day")
            }
            Text(ui.eventDays.toString())
            IconButton({ dispatch(VentAction.SetEventDays(ui.eventDays + 1)) }, enabled = ui.eventDays < MAX_EVENT_DAYS) {
                Icon(Icons.Outlined.Add, "Add event day")
            }
        }
        ui.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)
        ListItem(headlineContent = { Text("Allow attendee seat changes") }, trailingContent = {
            Switch(ui.seatSelectionOpen, { dispatch(VentAction.SetSeatSelectionOpen(it)) })
        }, colors = rowColors)
        ListItem(headlineContent = { Text(ui.session.room) }, overlineContent = { Text("Venue") },
            leadingContent = { Icon(Icons.Outlined.LocationOn, null) }, colors = rowColors)
    }
    Image(painterResource(Res.drawable.conference_stage), "Conference stage", Modifier.fillMaxWidth().height(space.heroIcon * 2)
        .clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
    OpsCard {
        OpsHeading(Icons.Outlined.Palette, "Current theme")
        val scheme = MaterialTheme.colorScheme
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            app.vent.domain.EventPalette.seeds.forEach { (name, seed) ->
                val accents = app.vent.domain.EventPalette.accents(seed, true)
                OutlinedCard(onClick = { previewSeed = seed },
                    modifier = Modifier.width(space.touch * 2).heightIn(min = space.touch * 2)) {
                    Column(Modifier.padding(space.sm), verticalArrangement = Arrangement.spacedBy(space.sm)) {
                        Text(name, style = MaterialTheme.typography.labelLarge)
                        Row {
                            listOf(accents.primary, accents.secondary, accents.tertiary).forEach { color ->
                                Surface(Modifier.weight(1f).height(space.xxl), color = Color(color)) {}
                            }
                        }
                        if ((previewSeed ?: app.vent.domain.EventPalette.seeds["Lavender"]) == seed) {
                            Icon(Icons.Outlined.Check, "Selected theme", tint = scheme.secondary)
                        }
                    }
                }
            }
        }
        Button({ dispatch(VentAction.SetEventAccent(previewSeed)) },
            Modifier.fillMaxWidth().height(space.action),
            enabled = previewReady && previewSeed != ui.eventAccent) { Text("Use this theme") }
    }
    OpsCard {
        OpsHeading(Icons.Outlined.Badge, "Pass preview")
        SummitPassCard(title.ifBlank { ui.eventTitle }, "Attendee name", Modifier.fillMaxWidth(), compact = true)
        Text("This is what attendees see.", style = MaterialTheme.typography.labelLarge)
    }
    if (title.trim() == ui.eventTitle) Text("No changes to save.", style = MaterialTheme.typography.labelLarge)
    Button({ dispatch(VentAction.RenameEvent(title)) }, Modifier.fillMaxWidth().height(space.action), enabled = title.isNotBlank() && title.trim() != ui.eventTitle) {
        Text("Save")
    }
    OutlinedButton({ dispatch(VentAction.Navigate(VentScreen.SCHEDULE)) }, Modifier.fillMaxWidth()) {
        Text("Preview Attendee Schedule")
    }
    if (onSignOut != null) TextButton(onSignOut, Modifier.fillMaxWidth()) { Text("Sign out") }
    }
    }
}

@Composable
private fun PalettePreview(scheme: ColorScheme) {
    val space = MaterialTheme.spacing
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(space.lg)) {
        PaletteSwatch("Primary", scheme.primary)
        PaletteSwatch("Secondary", scheme.secondary)
        PaletteSwatch("Tertiary", scheme.tertiary)
        PaletteSwatch("Surface", scheme.surface)
    }
    Spacer(Modifier.height(space.xs))
}

@Composable
private fun PaletteSwatch(label: String, color: Color) {
    val space = MaterialTheme.spacing
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(space.xs)) {
        Surface(Modifier.size(space.huge), shape = MaterialTheme.shapes.extraLarge, color = color) {}
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Preview @Composable private fun DoorOperationsPreview() = OperationsPreview(VentScreen.DOOR)
@Preview @Composable private fun OccupancyOperationsPreview() = OperationsPreview(VentScreen.OCCUPANCY)
@Preview @Composable private fun LiveOperationsPreview() = OperationsPreview(VentScreen.CONTROLS)
@Preview @Composable private fun SetupOperationsPreview() = OperationsPreview(VentScreen.SETUP)
@Preview @Composable private fun DoorOperationsDarkPreview() = OperationsPreview(VentScreen.DOOR, true)
@Preview @Composable private fun OccupancyOperationsDarkPreview() = OperationsPreview(VentScreen.OCCUPANCY, true)
@Preview @Composable private fun LiveOperationsDarkPreview() = OperationsPreview(VentScreen.CONTROLS, true)
@Preview @Composable private fun SetupOperationsDarkPreview() = OperationsPreview(VentScreen.SETUP, true)

@Composable
private fun OperationsPreview(screen: VentScreen, dark: Boolean = false) {
    VentTheme(darkTheme = dark) {
        Surface { OrganizerOperations(screen, VentUiState(role = DemoRole.ORGANIZER), dispatch = {}) }
    }
}
