package app.vent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessible
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Group
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.vent.ui.theme.spacing
import app.vent.imports.AttendeeFilter
import app.vent.imports.AttendeeImportIntent
import app.vent.imports.AttendeeImportState
import app.vent.imports.AttendeeImportViewModel
import app.vent.imports.ImportStage
import app.vent.event.EventIntent
import app.vent.event.EventViewModel
import app.vent.scanner.ScannerEffect
import app.vent.scanner.ScannerIntent
import app.vent.scanner.ScannerViewModel

@Composable
internal fun EventDetailScreen(uiState: VentUiState, onAction: (VentAction) -> Unit, expanded: Boolean) {
    val space = MaterialTheme.spacing
    val session = uiState.session
    val details = uiState.sessionDetails[session] ?: SessionDetails(session.title, session.time, session.room, session.speaker)
    var editing by rememberSaveable(session) { mutableStateOf(false) }
    var reminder by rememberSaveable(session) { mutableStateOf(false) }
    var rating by rememberSaveable(session) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = space.xxl),
        verticalArrangement = Arrangement.spacedBy(space.lg)) {
        item { EventHero(session, details) { onAction(VentAction.Navigate(VentScreen.SCHEDULE)) } }
        item {
            Text(details.title, Modifier.padding(horizontal = space.lg),
                style = MaterialTheme.typography.headlineLarge)
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = if (expanded) space.huge else space.lg)) {
                Column(Modifier.padding(space.lg), verticalArrangement = Arrangement.spacedBy(space.lg)) {
                    DetailLine(Icons.Outlined.Schedule, "SESSION TIME", details.time)
                    DetailLine(Icons.Outlined.LocationOn, "LOCATION", details.room)
                    DetailLine(Icons.Outlined.Group, "SPEAKERS", details.speaker)
                    if (uiState.role == DemoRole.ORGANIZER) {
                        Button({ editing = true }, Modifier.fillMaxWidth()) { Text("Edit session details") }
                        SessionStatus(details.phase)
                        if (details.phase != SessionPhase.ENDED) Button({
                            onAction(VentAction.SetSessionPhase(session,
                                if (details.phase == SessionPhase.LIVE) SessionPhase.ENDED else SessionPhase.LIVE))
                        }, Modifier.fillMaxWidth().height(space.action)) {
                            Text(if (details.phase == SessionPhase.LIVE) "End session" else "Start session")
                        }
                    } else {
                        DetailLine(Icons.Outlined.Chair, "ASSIGNED SEAT", uiState.confirmedSeat.ifBlank { "Not selected" })
                        Text("What to do here", style = MaterialTheme.typography.titleLarge)
                        SessionStatus(details.phase)
                        when (details.phase) {
                            SessionPhase.LIVE -> Button({ onAction(VentAction.Navigate(VentScreen.SCANNER)) },
                                Modifier.fillMaxWidth().height(space.action)) { Text("Check in") }
                            SessionPhase.UPCOMING -> {
                                Text("Opens 10 minutes before the session.")
                                Button({ reminder = true }, Modifier.fillMaxWidth().height(space.action)) { Text("Add reminder") }
                            }
                            SessionPhase.ENDED -> Button({ rating = true },
                                Modifier.fillMaxWidth().height(space.action)) {
                                Text(uiState.sessionRatings[session]?.let { "Your rating: $it / 5" } ?: "Rate this session")
                            }
                        }
                        if (details.phase == SessionPhase.LIVE) OutlinedButton({ reminder = true },
                            Modifier.fillMaxWidth().height(space.action)) { Text("Add reminder") }
                    }
                    if (details.phase != SessionPhase.ENDED || uiState.role == DemoRole.ORGANIZER)
                    OutlinedButton({ onAction(VentAction.Navigate(VentScreen.SEAT)) }, Modifier.fillMaxWidth().height(space.action)) {
                        Icon(Icons.Outlined.Chair, null); Spacer(Modifier.width(space.sm))
                        Text(if (uiState.role == DemoRole.ORGANIZER) "Edit seating" else if (uiState.confirmedSeat.isBlank()) "Select seat" else "View or change seat")
                    }
                }
            }
        }
    }
    if (reminder) ReminderSheet({ reminder = false }) { onAction(VentAction.SetReminder(session, it)) }
    if (rating) androidx.compose.material3.AlertDialog(
        onDismissRequest = { rating = false },
        title = { Text("Rate this session") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (1..5).forEach { value ->
                    androidx.compose.material3.FilledTonalButton({
                        onAction(VentAction.RateSession(session, value))
                        rating = false
                    }, contentPadding = PaddingValues(space.sm)) { Text("$value") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton({ rating = false }) { Text("Cancel") } },
    )
    if (editing && uiState.role == DemoRole.ORGANIZER) EditSessionDialog(details, { editing = false }, eventDays = uiState.eventDays,
        halls = (SummitSession.entries + uiState.sessionDetails.keys).map { uiState.sessionDetails[it]?.room ?: it.room }.distinct()) {
        onAction(VentAction.SaveSessionDetails(session, it))
        editing = false
    }
}

@Composable private fun EventHero(session: SummitSession, details: SessionDetails, onBack: () -> Unit) {
    val space = MaterialTheme.spacing
    val ink = app.vent.ui.theme.SessionHeaderForeground
    Box(Modifier.fillMaxWidth().height(space.action * 4 - space.xs)) {
        SessionHeaderImage(details.headerImage, Modifier.matchParentSize())
        if (details.headerImage != null) Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .82f)))
        Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(space.lg), verticalArrangement = Arrangement.spacedBy(space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Back to Sessions", tint = ink) }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.scrim.copy(alpha = .42f)) {
                Text(session.track, Modifier.padding(horizontal = space.md, vertical = space.xs), color = ink, style = MaterialTheme.typography.labelLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = ink); Text("Day ${details.day}", color = ink)
            }
        }
    }
}

@Composable private fun DetailLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(MaterialTheme.spacing.giant), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(icon, null) } }
        Column { Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(value, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable private fun SessionDetailRow(title: String, detail: String, status: String, onClick: () -> Unit) {
    val space = MaterialTheme.spacing
    OutlinedCard(Modifier.padding(horizontal = space.xl).fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(space.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(space.lg)); Text(detail, style = MaterialTheme.typography.bodySmall)
                }
            }
            AssistChip(onClick = onClick, label = { Text(status) })
        }
    }
}

@Composable
internal fun ScannerScreen(onBack: () -> Unit, onScanned: () -> Unit) {
    val viewModel = viewModel { ScannerViewModel() }
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect -> if (effect == ScannerEffect.CheckInCompleted) onScanned() }
    }
    val space = MaterialTheme.spacing
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.inverseSurface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(space.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(space.xl)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.inverseOnSurface) }
                Spacer(Modifier.weight(1f)); AssistChip(onClick = {}, label = { Text("●  Vent Live Scanner") }); Spacer(Modifier.weight(1f))
                IconButton({}) { Icon(Icons.Outlined.HelpOutline, "Help", tint = MaterialTheme.colorScheme.inverseOnSurface) }
            }
            Spacer(Modifier.weight(1f))
            Text("Scan the code on the screen or poster", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Align QR code within the frame to check in", color = MaterialTheme.colorScheme.outline)
            ScannerFrame(Modifier.fillMaxWidth().widthIn(max = space.compactBreakpoint).aspectRatio(1f).clickable { viewModel.onIntent(ScannerIntent.CodeDetected) })
            OutlinedButton(onClick = { viewModel.onIntent(ScannerIntent.ToggleTorch) }, modifier = Modifier.widthIn(min = space.heroIcon * 4).heightIn(min = space.action)) { Icon(Icons.Outlined.FlashlightOn, null); Spacer(Modifier.width(space.sm)); Text(if (state.torchEnabled) "Torch on" else "Torch") }
            TextButton(onClick = { viewModel.onIntent(ScannerIntent.CodeDetected) }) { Text("Enter code manually") }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable private fun ScannerFrame(modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.inversePrimary
    Canvas(modifier.semantics { contentDescription = "QR scanner frame. Tap to simulate a successful scan." }) {
        val corner = size.minDimension / 10
        drawRoundRect(outline.copy(alpha = .35f), style = Stroke(width = corner / 12), cornerRadius = CornerRadius(corner / 2))
        listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height)).forEach { point ->
            val sx = if (point.x == 0f) 1f else -1f; val sy = if (point.y == 0f) 1f else -1f
            drawLine(outline, point, Offset(point.x + sx * corner, point.y), corner / 8, StrokeCap.Square)
            drawLine(outline, point, Offset(point.x, point.y + sy * corner), corner / 8, StrokeCap.Square)
        }
        drawLine(outline, Offset(corner / 2, size.height / 2), Offset(size.width - corner / 2, size.height / 2), corner / 16)
    }
}

private data class AttendeeRow(val initials: String, val department: String, val access: Boolean = false, val exit: Boolean = false)

@Composable
internal fun AttendeeImportScreen(emails: Set<String>, dispatch: (VentAction) -> Unit) {
    var entry by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun add(text: String?) {
        if (text == null || attendeeEmails(text).isEmpty()) {
            error = "No email addresses could be read."
        } else {
            dispatch(VentAction.AddAttendees(text))
            entry = false
            input = ""
            error = null
        }
    }
    val picker = rememberAttendeeFilePicker(::add)
    val viewModel = viewModel { AttendeeImportViewModel() }
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(MaterialTheme.spacing.lg)) {
        Text("Attendees (${emails.size})", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Button({ if (picker != null) picker() else entry = true }) { Text("Upload CSV") }
            OutlinedButton({ entry = true }) { Text("Add attendees") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(state.query, { viewModel.onIntent(AttendeeImportIntent.Search(it)) },
            Modifier.fillMaxWidth(), label = { Text("Search email addresses") }, singleLine = true)
        LazyColumn {
            if (emails.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                    Icon(Icons.Outlined.Group, null, Modifier.size(MaterialTheme.spacing.heroIcon))
                    Text("No attendees yet", style = MaterialTheme.typography.headlineSmall)
                    Text("Add email addresses or upload a CSV to invite attendees.")
                    Button({ entry = true }) { Text("Add attendees") }
                }
            }
            items(emails.sorted().filter { it.contains(state.query, ignoreCase = true) }) { email ->
                Row(Modifier.fillMaxWidth().heightIn(min = MaterialTheme.spacing.touch + MaterialTheme.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(email, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    AssistChip({}, { Text("Registered") }, leadingIcon = { Icon(Icons.Outlined.CheckCircle, null) })
                }
            }
        }
    }
    if (entry) androidx.compose.material3.AlertDialog(
        onDismissRequest = { entry = false },
        title = { Text("Add attendees") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                OutlinedTextField(input, { input = it.take(1_048_576) },
                    label = { Text("Email addresses") }, placeholder = { Text("name@example.com, one per line") })
                Text("Or upload a CSV with an email column.", style = MaterialTheme.typography.labelLarge)
                if (attendeeEmails(input).isEmpty()) Text("Add at least one email.", style = MaterialTheme.typography.labelLarge)
            }
        },
        confirmButton = { Button({ add(input) }, enabled = attendeeEmails(input).isNotEmpty()) { Text("Save attendees") } },
        dismissButton = { TextButton({ entry = false }) { Text("Cancel") } },
    )
}

@Composable private fun AttendeeList(state: AttendeeImportState, onIntent: (AttendeeImportIntent) -> Unit) {
    val space = MaterialTheme.spacing
    val attendees = listOf(AttendeeRow("AM", "Product"), AttendeeRow("BK", "Engineering", exit = true), AttendeeRow("CT", "Design", true, true), AttendeeRow("DR", "Marketing"), AttendeeRow("ES", "Data & Analytics", exit = true), AttendeeRow("FH", "Operations", true), AttendeeRow("GP", "Legal", exit = true))
    Page("Attendees (68)", actions = { IconButton({ onIntent(AttendeeImportIntent.StartImport) }) { Icon(Icons.Outlined.UploadFile, "Import attendees") }; IconButton({}) { Icon(Icons.Outlined.HelpOutline, "Help") } }) {
        LazyColumn(contentPadding = PaddingValues(space.xl), verticalArrangement = Arrangement.spacedBy(space.lg)) {
            item { OutlinedTextField(state.query, { onIntent(AttendeeImportIntent.Search(it)) }, Modifier.fillMaxWidth(), placeholder = { Text("Search attendees") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (state.query.isNotEmpty()) IconButton({ onIntent(AttendeeImportIntent.Search("")) }) { Icon(Icons.Outlined.Close, "Clear") } }, singleLine = true) }
            item {
                OutlinedCard { Column(Modifier.fillMaxWidth().padding(space.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button({ onIntent(AttendeeImportIntent.StartImport) }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CloudUpload, null); Spacer(Modifier.width(space.sm)); Text("Upload CSV") }
                    Row(Modifier.padding(vertical = space.md), verticalAlignment = Alignment.CenterVertically) { HorizontalDivider(Modifier.weight(1f)); Text("  OR  "); HorizontalDivider(Modifier.weight(1f)) }
                    TextButton({ onIntent(AttendeeImportIntent.StartImport) }) { Icon(Icons.Outlined.Email, null); Spacer(Modifier.width(space.sm)); Text("Paste list of emails") }
                } }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) { FilterChip(state.selectedFilter == AttendeeFilter.ALL, { onIntent(AttendeeImportIntent.SelectFilter(AttendeeFilter.ALL)) }, { Text("All (68)") }, leadingIcon = { Icon(Icons.Outlined.Check, null) }); FilterChip(state.selectedFilter == AttendeeFilter.UNSEATED, { onIntent(AttendeeImportIntent.SelectFilter(AttendeeFilter.UNSEATED)) }, { Text("Unseated (14)") }); FilterChip(state.selectedFilter == AttendeeFilter.ACCESS, { onIntent(AttendeeImportIntent.SelectFilter(AttendeeFilter.ACCESS)) }, { Text("Access") }, leadingIcon = { Icon(Icons.Outlined.Accessible, null) }) } }
            items(attendees.filter { state.query.isBlank() || it.department.contains(state.query, true) || it.initials.contains(state.query, true) }) { person ->
                Row(Modifier.fillMaxWidth().heightIn(min = space.heroIcon).clickable(onClick = {}).padding(space.md), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(space.giant), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Text(person.initials, color = MaterialTheme.colorScheme.primary) } }
                    Column(Modifier.padding(horizontal = space.md).weight(1f)) { Text("Attendee ${person.initials}", fontWeight = FontWeight.SemiBold); Text(person.department, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (person.access) AssistChip({}, { Text("Access") }, leadingIcon = { Icon(Icons.Outlined.Accessible, null) })
                    if (person.exit) AssistChip({}, { Text("Exit row") })
                    Icon(Icons.Outlined.ChevronRight, "Open attendee")
                }; HorizontalDivider()
            }
        }
    }
}

@Composable private fun ColumnMapping(onClose: () -> Unit, onReview: () -> Unit) {
    val space = MaterialTheme.spacing
    ImportScaffold("Step 2 of 3: Column Mapping", "66% Completed", onClose) {
        Text("✓ Upload    Mapping    Review & Import", style = MaterialTheme.typography.titleMedium)
        OutlinedCard { Row(Modifier.fillMaxWidth().padding(space.xl), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.UploadFile, null); Column(Modifier.padding(horizontal = space.lg).weight(1f)) { Text("attendees_export.csv", fontWeight = FontWeight.Bold); Text("184 KB · 142 rows found") }; TextButton({}) { Text("Change") } } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Field Association", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.AutoAwesome, null); Text(" 4 of 5 auto-matched") }
        Text("Confirm how CSV headers align with Vent database properties.")
        MappingCard("Full Name", "Full Name (Attendee)", "Alice Morgan    Bob K.")
        MappingCard("Work Email", "Work Email", "alice@company.com")
        MappingCard("Department", "Department", "Product    Engineering")
        Button(onReview, Modifier.fillMaxWidth()) { Text("Review & Import") }
    }
}

@Composable private fun MappingCard(column: String, target: String, preview: String) {
    val space = MaterialTheme.spacing
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(space.xl), verticalArrangement = Arrangement.spacedBy(space.md)) {
        Row { Text("CSV COLUMN", modifier = Modifier.weight(1f)); Text(column, fontWeight = FontWeight.Bold); Icon(Icons.Outlined.CheckCircle, "Auto-matched") }
        Row { Text("Target Field", modifier = Modifier.weight(1f)); Text("* Required") }
        OutlinedButton({}, Modifier.fillMaxWidth()) { Text(target, modifier = Modifier.weight(1f)); Text("⌄") }
        Text("Previews:   $preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}

@Composable private fun ImportReview(onBack: () -> Unit, onDone: () -> Unit) {
    val space = MaterialTheme.spacing
    ImportScaffold("Step 3 of 3: Review & Import", "Ready", onBack) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) { Row(Modifier.padding(space.lg)) { Icon(Icons.Outlined.ErrorOutline, "Issues", tint = MaterialTheme.colorScheme.error); Column(Modifier.padding(start = space.md)) { Text("6 rows need attention", fontWeight = FontWeight.Bold); Text("Fix or skip invalid entries before importing.") } } }
        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) { FilterChip(true, {}, { Text("All (142)") }); FilterChip(false, {}, { Text("Issues (6)") }); FilterChip(false, {}, { Text("Valid (136)") }) }
        OutlinedCard { Column(Modifier.padding(space.lg), verticalArrangement = Arrangement.spacedBy(space.md)) { Text("Row 47 · Invalid work email", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold); OutlinedTextField("danir.@company.com", {}, Modifier.fillMaxWidth(), label = { Text("Work Email") }, isError = true); Row { OutlinedButton({}, Modifier.weight(1f)) { Text("Skip row") }; Spacer(Modifier.width(space.sm)); Button({}, Modifier.weight(1f)) { Text("Apply fix") } } } }
        OutlinedCard { Column(Modifier.padding(space.lg), verticalArrangement = Arrangement.spacedBy(space.md)) { Text("Row 92 · Missing department", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold); OutlinedButton({}, Modifier.fillMaxWidth()) { Text("Select department", Modifier.weight(1f)); Text("⌄") }; OutlinedButton({}, Modifier.fillMaxWidth()) { Text("Skip row") } } }
        Button(onDone, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(space.sm)); Text("Import 136 Attendees") }
        Text("Skipping 6 problematic rows", Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ImportScaffold(title: String, status: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val space = MaterialTheme.spacing
    Scaffold(topBar = { Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = space.xl), verticalAlignment = Alignment.CenterVertically) { Text("Import Attendees", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); IconButton(onClose) { Icon(Icons.Outlined.Close, "Close") } } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(space.xl), verticalArrangement = Arrangement.spacedBy(space.lg)) {
            item { Row { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(status, style = MaterialTheme.typography.titleMedium) } }
            item { Column(verticalArrangement = Arrangement.spacedBy(space.lg), content = content) }
        }
    }
}
