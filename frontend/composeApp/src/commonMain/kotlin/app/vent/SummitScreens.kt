package app.vent

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import app.vent.ui.theme.success
import androidx.compose.ui.graphics.vector.ImageVector
import app.vent.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vent.architecture.MviViewModel
import app.vent.ui.theme.spacing

internal val staffScreens = linkedMapOf(
    VentScreen.DOOR to "Door scan", VentScreen.OCCUPANCY to "Occupancy",
    VentScreen.CONTROLS to "Live controls", VentScreen.SETUP to "Event setup",
)

private data class PanelState(val choice: String = "", val input: String = "", val flags: Set<String> = emptySet())
private sealed interface PanelIntent {
    data class Choose(val value: String) : PanelIntent
    data class Edit(val value: String) : PanelIntent
    data class Toggle(val value: String) : PanelIntent
}
private open class PanelModel : MviViewModel<PanelState, PanelIntent, Nothing>(PanelState()) {
    override fun onIntent(intent: PanelIntent) = reduce {
        when (intent) {
            is PanelIntent.Choose -> copy(choice = intent.value)
            is PanelIntent.Edit -> copy(input = intent.value)
            is PanelIntent.Toggle -> copy(flags = if (intent.value in flags) flags - intent.value else flags + intent.value)
        }
    }
}

private class ScheduleViewModel : PanelModel()
private class QuestsViewModel : PanelModel()
private class ProfileViewModel : PanelModel()
private class WrappedViewModel : PanelModel()
private class ProjectorViewModel : PanelModel()
private class ConfirmedViewModel : PanelModel()
private class DuplicateViewModel : PanelModel()
private class WrongViewModel : PanelModel()
private class ExpiredViewModel : PanelModel()

@Composable private fun screenModel(screen: VentScreen): PanelModel = when (screen) {
    VentScreen.SCHEDULE -> viewModel<ScheduleViewModel> { ScheduleViewModel() }
    VentScreen.QUESTS -> viewModel<QuestsViewModel> { QuestsViewModel() }
    VentScreen.PROFILE -> viewModel<ProfileViewModel> { ProfileViewModel() }
    VentScreen.WRAPPED -> viewModel<WrappedViewModel> { WrappedViewModel() }
    VentScreen.PROJECTOR -> viewModel<ProjectorViewModel> { ProjectorViewModel() }
    VentScreen.CONFIRMED -> viewModel<ConfirmedViewModel> { ConfirmedViewModel() }
    VentScreen.DUPLICATE -> viewModel<DuplicateViewModel> { DuplicateViewModel() }
    VentScreen.WRONG -> viewModel<WrongViewModel> { WrongViewModel() }
    VentScreen.EXPIRED -> viewModel<ExpiredViewModel> { ExpiredViewModel() }
    else -> error("Unexpected summit destination: $screen")
}

@Composable private fun Summary(label: String, title: String, detail: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable internal fun ScanEntry(ui: VentUiState, dispatch: (VentAction) -> Unit) {
    val model = viewModel(key = "scanner-input") { PanelModel() }
    val state by model.uiState.collectAsState()
    fun submit(code: String) {
        val result = when (code) {
            ui.session.passcode -> if (ui.isCheckedIn) VentScreen.DUPLICATE else VentScreen.CONFIRMED
            "093000" -> VentScreen.EXPIRED
            else -> VentScreen.WRONG
        }
        if (result == VentScreen.CONFIRMED) dispatch(VentAction.CheckIn)
        model.onIntent(PanelIntent.Choose(""))
        model.onIntent(PanelIntent.Edit(""))
        dispatch(VentAction.Navigate(result))
    }
    val spacing = MaterialTheme.spacing
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ dispatch(VentAction.Navigate(VentScreen.SCHEDULE)) }) {
                Icon(Icons.Outlined.ArrowBack, "Back to sessions")
            }
            Text("Check in", style = MaterialTheme.typography.titleLarge)
        }
        Text(ui.session.title)
        if (state.choice == "manual") Spacer(Modifier.weight(1f))
        else Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = spacing.compactBreakpoint)
                .fillMaxWidth().weight(1f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(Modifier.fillMaxSize()) {
                CheckInCamera(Modifier.fillMaxSize(), ::submit)
                Surface(Modifier.align(Alignment.TopCenter).padding(spacing.lg),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text("Point at the session QR code.", Modifier.padding(spacing.sm))
                }
            }
        }
        OutlinedButton({ model.onIntent(PanelIntent.Choose("manual")) }, Modifier.fillMaxWidth()) {
            Text("Enter code instead")
        }
    }
    if (state.choice == "manual") AlertDialog(
        onDismissRequest = { model.onIntent(PanelIntent.Choose("")) },
        title = { Text("Enter session code") },
        text = { OutlinedTextField(state.input, { model.onIntent(PanelIntent.Edit(it.filter(Char::isDigit).take(6))) },
            singleLine = true, label = { Text("6-digit code") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
            supportingText = { Text("Ask staff for the session code. Enter the six-digit code.") }) },
        confirmButton = { Button({ submit(state.input) }, enabled = state.input.length == 6) { Text("Check in") } },
        dismissButton = { TextButton({ model.onIntent(PanelIntent.Choose("")) }) { Text("Cancel") } },
    )
}

@Composable internal fun SummitScreen(screen: VentScreen, ui: VentUiState, dispatch: (VentAction) -> Unit,
    email: String = "", signOut: () -> Unit = {}) {
    val model = screenModel(screen)
    val state by model.uiState.collectAsState()
    val space = MaterialTheme.spacing
    fun go(target: VentScreen) = dispatch(VentAction.Navigate(target))
    val showQuestAction = screen == VentScreen.QUESTS && ui.role == DemoRole.ORGANIZER
    var createQuest by rememberSaveable { mutableStateOf(false) }
    val title = staffScreens[screen] ?: when (screen) {
        VentScreen.SCHEDULE -> "Sessions"
        VentScreen.QUESTS -> "Quests"
        VentScreen.PROFILE -> "Profile"
        VentScreen.WRAPPED -> "Your summit journey"
        else -> "Attendee verification"
    }
    Page(title) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(space.lg)
            .padding(bottom = if (showQuestAction) space.heroIcon else space.none),
            verticalArrangement = Arrangement.spacedBy(space.md)) {
            if (screen in staffScreens) {
                Text("Live operations · Demo console", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    staffScreens.filterKeys { it != VentScreen.DOOR }.forEach { (destination, label) ->
                        FilterChip(screen == destination, { go(destination) }, { Text(label) })
                    }
                }
            }
            when (screen) {
                VentScreen.SCHEDULE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        listOf("Day 1", "Day 2").forEach { day ->
                            FilterChip((state.choice.ifEmpty { "Day 1" }) == day, { model.onIntent(PanelIntent.Choose(day)) }, { Text(day) })
                        }
                    }
                    if (state.choice == "Day 2") Summary("Day 2", "Schedule pending", "Sessions will appear here when published.")
                    else {
                        Summary("Happening now · Stage 1", "The AI-Native Enterprise Architecture", "10:00–11:15 AM · Auditorium A · Seat " + ui.confirmedSeat)
                        if (ui.role == DemoRole.ATTENDEE) {
                            Button({ go(VentScreen.SCANNER) }) { Text("Open check-in") }
                        }
                        Text("Upcoming today", style = MaterialTheme.typography.titleLarge)
                        Summary("11:30 AM · Hall B", "Zero-Trust Infrastructure for Multi-Agent AI", "Security & operations")
                        Summary("1:30 PM · Auditorium A", "Autonomous Data Pipelines at Scale", "Data platforms · Open seating")
                        Summary("3:00 PM · Auditorium C", "Developer Velocity with Modern LLM Tooling", "Interactive workshop")
                    }
                    OutlinedButton({ go(VentScreen.SEAT) }) { Text("Open seat navigator") }
                }
                VentScreen.QUESTS -> {
                    val responses = ui.questResponses[email].orEmpty()
                    if (ui.role == DemoRole.ATTENDEE) {
                    val earned = ui.questDefinitions.filter { it.title in responses }.sumOf { it.points }
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Text("$earned points", Modifier.padding(space.xxl), style = MaterialTheme.typography.displaySmall)
                    }
                    }
                    if (ui.questDefinitions.isEmpty() && ui.customQuests.isEmpty()) {
                        Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(space.heroIcon).align(Alignment.CenterHorizontally))
                        Text("No quests yet", style = MaterialTheme.typography.headlineSmall)
                        Text("Quests give attendees points for actions, polls, and quizzes.")
                        if (ui.role == DemoRole.ORGANIZER) Button({ createQuest = true },
                            Modifier.fillMaxWidth().height(space.action)) { Text("Create your first quest") }
                    }
                    ui.customQuests.forEach { Summary("Conference quest", it, "Available") }
                    ui.questDefinitions.forEach { quest ->
                        Card(Modifier.fillMaxWidth().heightIn(min = space.heroIcon + space.lg)
                            .alpha(if (quest.title in responses) .6f else 1f)) {
                            Column(Modifier.padding(space.md), verticalArrangement = Arrangement.spacedBy(space.xs)) {
                                Text("${quest.kind} · ${quest.points} points", style = MaterialTheme.typography.labelLarge)
                                Text(quest.title, style = MaterialTheme.typography.titleMedium)
                                Text(quest.prompt)
                                if (quest.title in responses) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.success)
                                    Text("Claimed +${quest.points}", color = MaterialTheme.colorScheme.success)
                                }
                                else if (ui.role == DemoRole.ATTENDEE) {
                                    val choices = if (quest.kind == "Action") listOf("Completed") else quest.options
                                    choices.forEach { answer ->
                                        OutlinedButton({ dispatch(VentAction.CompleteQuest(quest.title, answer)) }) { Text(answer) }
                                    }
                                } else quest.options.forEach { Text(it) }
                            }
                        }
                    }
                    ui.message?.let { Text(it) }
                }
                VentScreen.WRAPPED -> {
                    Text("Your 2025 summit journey", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelLarge)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Column(Modifier.fillMaxWidth().padding(space.xl), verticalArrangement = Arrangement.spacedBy(space.lg)) {
                            Text(ui.eventTitle + " · Wrapped")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.md)) {
                                Portrait(email)
                                Column { Text(email.substringBefore('@'), style = MaterialTheme.typography.headlineSmall); Text("Your summit journey") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                                Tile(Icons.Outlined.EventAvailable, "Sessions", "4", Modifier.weight(1f))
                                Tile(Icons.Outlined.Chair, "Keynote seat", ui.confirmedSeat, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                                Tile(Icons.Outlined.HowToVote, "Interactions", "6 polls", Modifier.weight(1f))
                                Tile(Icons.Outlined.EmojiEvents, "Achievements", "5 badges", Modifier.weight(1f))
                            }
                            Text("Rank #12 of 480 Attendees", style = MaterialTheme.typography.titleMedium)
                            Text("Overall Community Score · 980 XP")
                        }
                    }
                    Summary("Attendance certificate", "Technical track completed", "Demo achievement · Vent Demo Day")
                    Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        Tile(Icons.Outlined.Schedule, "Total time", "14h 45m", Modifier.weight(1f))
                        Tile(Icons.Outlined.Group, "Connections", "38 scans", Modifier.weight(1f))
                    }
                    OutlinedButton({ go(VentScreen.PROFILE) }) { Text("View summit pass") }
                }
                VentScreen.PROFILE -> {
                    var showPass by rememberSaveable { mutableStateOf(false) }
                    Card {
                        Column(Modifier.fillMaxWidth().padding(space.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(space.md)) {
                            Portrait(email)
                            Text(email, style = MaterialTheme.typography.titleLarge)
                            Text(ui.eventTitle)
                            Text("All-access attendee", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Text("Summit Details & Status", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                        Tile(Icons.Outlined.Chair, "Seat", ui.confirmedSeat.ifBlank { "None" }, Modifier.weight(1f))
                        Tile(Icons.Outlined.Bolt, "Points", ui.questDefinitions.filter { it.title in ui.questResponses[email].orEmpty() }.sumOf { it.points }.toString(), Modifier.weight(1f))
                        Tile(Icons.Outlined.FactCheck, "Sessions", ui.seating.values.count { it.checkedIn }.toString(), Modifier.weight(1f))
                    }
                    Card(onClick = { showPass = true }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Your pass") },
                            supportingContent = { Text("All-access · View pass") },
                            leadingContent = { Icon(Icons.Outlined.QrCode, null) },
                            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                        )
                    }
                    if (showPass) androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showPass = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                    ) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                                .padding(space.xxl), verticalArrangement = Arrangement.spacedBy(space.lg)) {
                                TextButton({ showPass = false }) {
                                    Icon(Icons.Outlined.Close, null)
                                    Text("Close pass")
                                }
                                SummitPassCard(ui.eventTitle, email, Modifier.fillMaxWidth(),
                                    demo = email == "guest@vent.invalid")
                                Text("A verified pass is required to show a code to staff.",
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    var showPreferences by rememberSaveable { mutableStateOf(false) }
                    var draftPreferences by remember { mutableStateOf(ui.preferences) }
                    Card(onClick = { draftPreferences = ui.preferences; showPreferences = true }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(headlineContent = { Text("Conference preferences") },
                            supportingContent = { Text("Badge sharing, leaderboard visibility, and reminders") },
                            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                    }
                    if (showPreferences) AlertDialog(
                        onDismissRequest = { showPreferences = false },
                        title = { Text("Conference preferences") },
                        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    listOf(
                        Triple("Peer exchange", "Allow badge sharing", "Let other attendees exchange contact details with you."),
                        Triple("Leaderboard visibility", "Show me on the leaderboard", "Include your score in the conference rankings."),
                        Triple("Session reminders", "Remind me about sessions", "Receive reminders for sessions you plan to attend."),
                    ).forEach { (key, label, description) ->
                        ListItem(headlineContent = { Text(label) }, supportingContent = { Text(description) }, trailingContent = {
                            Switch(key in draftPreferences, {
                                draftPreferences = if (it) draftPreferences + key else draftPreferences - key
                            }, colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.secondary,
                                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                            ))
                        })
                    }
                        } },
                        confirmButton = {
                            Button({
                                (draftPreferences + ui.preferences).filter {
                                    (it in draftPreferences) != (it in ui.preferences)
                                }.forEach { dispatch(VentAction.TogglePreference(it)) }
                                showPreferences = false
                            }, Modifier.fillMaxWidth().height(space.action)) { Text("Save") }
                        },
                    )
                    Spacer(Modifier.height(space.xxl))
                    TextButton(signOut) {
                        Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error)
                        Text("Sign out", color = MaterialTheme.colorScheme.error)
                    }
                }
                VentScreen.PROJECTOR -> {
                    Text(ui.eventTitle + " · Hall A", style = MaterialTheme.typography.labelLarge)
                    Text(if (ui.pollPaused) "Audience poll paused" else "Audience poll open")
                    Summary("Mandatory session check-in", ui.session.passcode.chunked(3).joinToString(" "), ui.session.title)
                    Summary("Morning plenary keynote", "The AI-Native Enterprise Architecture", "10:00–11:15 AM · Auditorium A")
                    LinearProgressIndicator({ .71f }, Modifier.fillMaxWidth())
                    Text("342 / 480 seats · 71% occupancy")
                }
                else -> {
                    val (heading, detail) = when (screen) {
                        VentScreen.CONFIRMED -> "Check-in confirmed" to "Your attendance is recorded."
                        VentScreen.DUPLICATE -> "Already checked in" to "Your existing attendance record is safe."
                        VentScreen.EXPIRED -> "Check-in window closed" to "Morning plenary check-in closed at 9:30 AM."
                        else -> "Wrong session code" to "Use the code displayed at the keynote in Auditorium A."
                    }
                    Summary("Session verification", heading, detail)
                    Summary("Registered session", ui.session.title, ui.session.room + " · " + ui.confirmedSeat.ifBlank { "Open seating" })
                    OutlinedButton({ go(VentScreen.SEAT) }) { Text("View assigned seat and map") }
                    Button({ go(if (screen == VentScreen.WRONG || screen == VentScreen.EXPIRED) VentScreen.SCANNER else VentScreen.SCHEDULE) }) {
                        Text(if (screen == VentScreen.WRONG || screen == VentScreen.EXPIRED) "Scan again" else "Return to schedule")
                    }
                }
            }
        }
        if (showQuestAction) {
            FloatingActionButton({ createQuest = true }, Modifier.align(Alignment.BottomEnd).padding(space.lg)) {
                Icon(Icons.Outlined.Add, "Add quest")
            }
        }
        }
    }
    if (createQuest && showQuestAction) {
        CreateQuestDialog({ createQuest = false }) {
            dispatch(VentAction.PublishQuest(it))
            createQuest = false
        }
    }
}

@Composable private fun Portrait(email: String) {
    Surface(Modifier.size(MaterialTheme.spacing.heroIcon), shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Text(email.substringBefore('@').take(2).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable private fun Tile(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            Icon(icon, null)
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
