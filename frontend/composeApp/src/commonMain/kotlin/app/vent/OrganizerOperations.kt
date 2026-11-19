package app.vent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.vent.composeapp.generated.resources.auditorium
import app.vent.ui.theme.VentTheme
import app.vent.ui.theme.spacing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
internal fun OrganizerOperations(screen: VentScreen, ui: VentUiState, dispatch: (VentAction) -> Unit, onAccentChange: (Int?) -> Unit = {}) {
    if (ui.role != DemoRole.ORGANIZER) return
    val space = MaterialTheme.spacing
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        IconButton({ dispatch(VentAction.Navigate(VentScreen.PROFILE)) }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to settings")
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = space.lg),
            horizontalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            organizerOperationTabs.forEach { (destination, label) ->
                FilterChip(screen == destination, { dispatch(VentAction.Navigate(destination)) }, { Text(label) })
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = space.detailBreakpoint).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(space.lg),
                verticalArrangement = Arrangement.spacedBy(space.lg),
            ) {
                when (screen) {
                    VentScreen.DOOR -> DoorOperations(ui, dispatch)
                    VentScreen.OCCUPANCY -> OccupancyOperations(ui, dispatch)
                    VentScreen.CONTROLS -> LiveOperations(ui, dispatch)
                    else -> SetupOperations(ui, dispatch, onAccentChange)
                }
            }
        }
    }
}

internal val organizerOperationTabs = linkedMapOf(
    VentScreen.SETUP to "Event setup",
    VentScreen.DOOR to "Door scan",
    VentScreen.OCCUPANCY to "Occupancy",
    VentScreen.CONTROLS to "Live controls",
)

@Composable
private fun OpsCard(content: @Composable ColumnScope.() -> Unit) {
    val space = MaterialTheme.spacing
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
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
        Canvas(Modifier.fillMaxWidth().height(space.heroIcon * 2)) {
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
        OpsHeading(Icons.Outlined.WarningAmber, "Anomaly Detection")
        Text("Live scan and collision alerts are unavailable.")
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
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun LiveOperations(ui: VentUiState, dispatch: (VentAction) -> Unit) {
    val space = MaterialTheme.spacing
    var createQuest by rememberSaveable { mutableStateOf(false) }
    OpsCard {
        OpsHeading(Icons.Outlined.Tune, ui.session.title)
        Text("${ui.session.room} · ${ui.session.time}", style = MaterialTheme.typography.bodySmall)
    }
    OpsHeading(Icons.Outlined.HowToVote, "Audience Poll Controller")
    OpsCard {
        OpsBadge(if (ui.pollPaused) "POLL PAUSED" else "DEMO POLL", emphasized = true)
        Text("Which engineering priority will have the highest impact?", style = MaterialTheme.typography.titleMedium)
        listOf("Agentic workflows" to .42f, "On-device models" to .28f, "Zero-trust infrastructure" to .18f, "Developer tooling" to .12f)
            .forEach { (label, share) ->
                Row {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator({ share }, Modifier.fillMaxWidth())
            }
        Button({ dispatch(VentAction.TogglePoll) }, Modifier.fillMaxWidth()) {
            Text(if (ui.pollPaused) "Resume voting" else "Freeze voting")
        }
    }
    OpsHeading(Icons.Outlined.Bolt, "Summit Quest & Stage Triggers")
    OpsCard {
        Text("Create a quest for this event", style = MaterialTheme.typography.titleMedium)
        Button({ createQuest = true }, Modifier.fillMaxWidth()) { Text("Create quest") }
    }
    if (createQuest) CreateQuestDialog({ createQuest = false }) {
        dispatch(VentAction.PublishQuest(it))
        createQuest = false
    }
}

@Composable
private fun SetupOperations(ui: VentUiState, dispatch: (VentAction) -> Unit, onAccentChange: (Int?) -> Unit) {
    val space = MaterialTheme.spacing
    var title by rememberSaveable(ui.eventTitle) { mutableStateOf(ui.eventTitle) }
    OpsCard {
        OpsHeading(Icons.Outlined.Palette, "Event Configuration & Theme Setup")
        Text("Set the event title and preview the event palette.", style = MaterialTheme.typography.bodyMedium)
    }
    OpsCard {
        OpsHeading(Icons.Outlined.CalendarMonth, "Summit Metadata")
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Event title") }, singleLine = true)
        ListItem(headlineContent = { Text(ui.session.room) }, overlineContent = { Text("Venue") },
            leadingContent = { Icon(Icons.Outlined.LocationOn, null) })
    }
    Image(painterResource(Res.drawable.auditorium), "Auditorium", Modifier.fillMaxWidth().height(space.heroIcon * 2)
        .clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
    OpsCard {
        OpsHeading(Icons.Outlined.Palette, "Current theme")
        val scheme = MaterialTheme.colorScheme
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            listOf("Teal" to null, "Sage" to 0xFFBCCBB0.toInt(), "Lavender" to 0xFFD0C1FF.toInt()).forEach { (name, argb) ->
                val swatch = argb?.let { Color(it) } ?: app.vent.ui.theme.VentDarkColors.primary
                FilterChip(
                    selected = scheme.primary == swatch,
                    onClick = { onAccentChange(argb) },
                    label = { Text(name) },
                    leadingIcon = { Surface(Modifier.size(space.md), shape = MaterialTheme.shapes.extraLarge, color = swatch) {} },
                )
            }
        }
        PalettePreview(MaterialTheme.colorScheme)
    }
    OpsCard {
        OpsHeading(Icons.Outlined.Badge, "Mobile Pass Preview")
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(space.lg)) {
                Text("All-Access Staff Pass", style = MaterialTheme.typography.titleMedium)
                Text("${title.ifBlank { ui.eventTitle }} · ${ui.activeGate}")
            }
        }
    }
    Button({ dispatch(VentAction.RenameEvent(title)) }, Modifier.fillMaxWidth(), enabled = title.isNotBlank() && title.trim() != ui.eventTitle) {
        Text("Save Event Configuration")
    }
    OutlinedButton({ dispatch(VentAction.Navigate(VentScreen.SCHEDULE)) }, Modifier.fillMaxWidth()) {
        Text("Preview Attendee Schedule")
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
        Text("#" + (color.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase(),
            style = MaterialTheme.typography.labelSmall)
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
