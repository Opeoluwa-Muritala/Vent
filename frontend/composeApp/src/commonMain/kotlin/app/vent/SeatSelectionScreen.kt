package app.vent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import app.vent.ui.theme.spacing
import kotlin.math.abs

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SeatSelectionScreen(uiState: VentUiState, onAction: (VentAction) -> Unit) {
    if (uiState.role == DemoRole.ORGANIZER) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val height = maxHeight
            androidx.compose.material3.BottomSheetScaffold(
                sheetPeekHeight = height * .55f,
                sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                sheetContent = {
                    SeatDetails(uiState, onAction, Modifier.fillMaxWidth().heightIn(max = height * .9f))
                },
            ) {
                Column(Modifier.fillMaxSize().padding(MaterialTheme.spacing.lg)) {
                    Text(uiState.session.room, style = MaterialTheme.typography.headlineLarge)
                    SeatMapPane(uiState, onAction, Modifier.fillMaxWidth().height(height * .4f))
                }
            }
        }
        return
    }
    Page("Your seat") {
        if (uiState.role == DemoRole.ATTENDEE) {
            ListItem(headlineContent = { Text(uiState.session.title) },
                supportingContent = { Text(uiState.session.room + " · " + uiState.confirmedSeat.ifBlank { "Select a seat" }) },
                trailingContent = { TextButton({ onAction(VentAction.Navigate(VentScreen.SCANNER)) }) { Text("Check in") } })
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(
                horizontal = MaterialTheme.spacing.xl,
                vertical = MaterialTheme.spacing.sm,
            ),
        ) {
            if (maxWidth >= MaterialTheme.spacing.detailBreakpoint) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xl)) {
                    SeatMapPane(uiState, onAction, Modifier.weight(1f).fillMaxHeight())
                    SeatDetails(uiState, onAction, Modifier.widthIn(
                        min = MaterialTheme.spacing.xxxl * 9,
                        max = MaterialTheme.spacing.huge * 9,
                    ).fillMaxHeight())
                }
            } else {
                val viewportHeight = maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                    SeatMapPane(uiState, onAction, Modifier.fillMaxWidth().height(viewportHeight.coerceAtLeast(MaterialTheme.spacing.heroIcon * 5)))
                    SeatDetails(uiState, onAction, Modifier.fillMaxWidth().heightIn(max = viewportHeight.coerceAtLeast(MaterialTheme.spacing.heroIcon * 4)))
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SeatMapPane(uiState: VentUiState, onAction: (VentAction) -> Unit, modifier: Modifier) {
    var selectionSheet by rememberSaveable { mutableStateOf(false) }
    var locateRequest by rememberSaveable { mutableStateOf(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiState.sessionDetails[uiState.session]?.room ?: uiState.session.room,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (uiState.role == DemoRole.ORGANIZER) {
                        "${uiState.venueRows.sumOf { it.seats.size }} seats · ${uiState.seatLayoutMode.name.lowercase()} layout"
                    } else {
                        uiState.confirmedSeat.ifBlank { "No seat selected" }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = { locateRequest++; onAction(VentAction.LocateConfirmedSeat) }) {
                Icon(Icons.Outlined.MyLocation, contentDescription = null)
                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                Text(if (uiState.role == DemoRole.ORGANIZER) "Fit map" else "Find my seat")
            }
        }
        CanvasVenueMap(
            ui = uiState,
            locateRequest = locateRequest,
            onSeatClick = { label ->
                if (uiState.role == DemoRole.ATTENDEE) selectionSheet = true
                onAction(
                    if (uiState.role == DemoRole.ORGANIZER) VentAction.ApplySeatEdit(label)
                    else VentAction.SelectSeat(label),
                )
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        if (uiState.role == DemoRole.ATTENDEE) SeatLegend()
    }
    if (selectionSheet && uiState.role == DemoRole.ATTENDEE) androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { selectionSheet = false },
    ) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)) {
            Text("Seat ${uiState.selectedSeat}", style = MaterialTheme.typography.titleLarge)
            Text("Confirm your seat for this session.")
            Button({ onAction(VentAction.ConfirmSeat); selectionSheet = false },
                Modifier.fillMaxWidth().height(MaterialTheme.spacing.action),
                enabled = uiState.seatSelectionOpen && uiState.selectedSeat != uiState.confirmedSeat) {
                Text("Choose this seat")
            }
        }
    }
}


@Composable
private fun SeatDetails(uiState: VentUiState, onAction: (VentAction) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            if (uiState.role == DemoRole.ORGANIZER) {
                OrganizerSeatControls(uiState, onAction)
            } else {
                Text(uiState.selectedSeat.ifBlank { "Choose a seat" }, style = MaterialTheme.typography.titleLarge)
                SeatLegend()
                if (!uiState.seatSelectionOpen) {
                    Text(
                        "Seat changes are closed for attendees. Your confirmed seat remains ${uiState.confirmedSeat}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (!uiState.seatSelectionOpen) "An organizer must reopen selection before you can move."
                    else if (uiState.confirmedSeat.isBlank()) "Select an available seat for this session."
                    else if (uiState.selectedSeat == uiState.confirmedSeat) "Choose an outlined seat to move from ${uiState.confirmedSeat}."
                    else "${uiState.selectedSeat} is available. Confirm to move from ${uiState.confirmedSeat}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uiState.seatSelectionOpen && uiState.selectedSeat.isNotBlank() && uiState.selectedSeat != uiState.confirmedSeat) {
                    Button(onClick = { onAction(VentAction.ConfirmSeat) }, modifier = Modifier.fillMaxWidth()) { Text("Confirm seat ${uiState.selectedSeat}") }
                }
            }
            uiState.message?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun OrganizerSeatControls(uiState: VentUiState, onAction: (VentAction) -> Unit) {
    val space = MaterialTheme.spacing
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmPublish by rememberSaveable { mutableStateOf(false) }
    var confirmRemoval by rememberSaveable { mutableStateOf(false) }
    val row = uiState.venueRows.firstOrNull { it.label == uiState.editingRowLabel }
    val assigned = uiState.seatAssignments[uiState.session].orEmpty()
    Text(if (uiState.layoutPublication == LayoutPublication.PUBLISHED) "Live: layout published" else "Draft: not published yet",
        style = MaterialTheme.typography.labelLarge)
    VentPillTabs(listOf("Seats", "Rows", "Publish"), tab) { tab = it }
    when (tab) {
        0 -> {
            Text("Seat type and availability", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                SeatType.entries.forEach { type ->
                    FilterChip(uiState.seatEditMode == SeatEditMode.TYPE && uiState.seatEditType == type,
                        { onAction(VentAction.SetSeatEditMode(SeatEditMode.TYPE)); onAction(VentAction.SetSeatEditType(type)) },
                        { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            Text("Select a type, then tap seats on the map.")
        }
        1 -> {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                uiState.venueRows.forEach { option ->
                    FilterChip(option.label == uiState.editingRowLabel,
                        { onAction(VentAction.SelectEditingRow(option.label)) }, { Text("Row ${option.label}") })
                }
            }
            row?.let {
                val index = uiState.venueRows.indexOf(row)
                val protected = assigned.values.toSet() + uiState.confirmedSeat
                Text("Row ${row.label}, ${row.seats.size} seats, ${row.seats.firstOrNull()?.tier?.name?.lowercase().orEmpty()}",
                    style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    OutlinedButton({ onAction(VentAction.AddSeatToRow) }, Modifier.weight(1f),
                        enabled = row.seats.size < MAX_SEATS_PER_ROW) { Text("Add seat") }
                    OutlinedButton({ onAction(VentAction.RemoveSeatFromRow) }, Modifier.weight(1f),
                        enabled = row.seats.size > MIN_SEATS_PER_ROW && row.seats.last().label !in protected) { Text("Remove seat") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    OutlinedButton({ onAction(VentAction.MoveEditingRow(-1)) }, Modifier.weight(1f), enabled = index > 0) { Text("Move up") }
                    OutlinedButton({ onAction(VentAction.MoveEditingRow(1)) }, Modifier.weight(1f),
                        enabled = index < uiState.venueRows.lastIndex) { Text("Move down") }
                }
                if (index == 0) Text("Row ${row.label} is at the top.", style = MaterialTheme.typography.labelLarge)
                OutlinedButton({ onAction(VentAction.ToggleEditingRowTier) }) { Text("Change row type") }
                OutlinedButton({ onAction(VentAction.AddSeatRow) }, enabled = uiState.venueRows.size < MAX_VENUE_ROWS) { Text("Add row") }
                Spacer(Modifier.height(space.xxl))
                TextButton({ confirmRemoval = true },
                    enabled = uiState.venueRows.size > 1 && row.seats.none { it.label in protected }) {
                    Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                    Text("Remove row", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        2 -> {
            Button({ onAction(VentAction.AutoAssignSeats) }, Modifier.fillMaxWidth().height(space.action),
                enabled = uiState.attendees.isNotEmpty()) { Text("Auto-seat attendees") }
            Text("${assigned.size} of ${uiState.attendees.size} attendees assigned")
            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(SeatAvailability.BLOCKED to "Block", SeatAvailability.AVAILABLE to "Unblock").forEachIndexed { index, (availability, label) ->
                    SegmentedButton(
                        selected = uiState.seatEditMode == SeatEditMode.AVAILABILITY && uiState.seatEditAvailability == availability,
                        onClick = { onAction(VentAction.SetSeatEditMode(SeatEditMode.AVAILABILITY)); onAction(VentAction.SetSeatEditAvailability(availability)) },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(label) }
                }
            }
            Text("Choose a mode, then tap seats on the map.")
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                SeatLayoutMode.entries.forEach { layout ->
                    FilterChip(uiState.seatLayoutMode == layout, { onAction(VentAction.SetSeatLayout(layout)) },
                        { Text(layout.name.lowercase()) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Attendee seat changes", Modifier.weight(1f))
                Switch(uiState.seatSelectionOpen, { onAction(VentAction.SetSeatSelectionOpen(it)) })
            }
            OutlinedButton({ onAction(VentAction.SaveLayoutDraft) }, Modifier.fillMaxWidth().height(space.action)) { Text("Save draft") }
            Button({ confirmPublish = true }, Modifier.fillMaxWidth().height(space.action)) { Text("Publish layout") }
        }
    }
    if (confirmPublish) AlertDialog(
        onDismissRequest = { confirmPublish = false },
        title = { Text("Publish layout?") },
        text = { Text("${uiState.attendees.size} attendees will see the published seating layout.") },
        confirmButton = { Button({ confirmPublish = false; onAction(VentAction.PublishLayout) }) { Text("Publish layout") } },
        dismissButton = { TextButton({ confirmPublish = false }) { Text("Cancel") } },
    )
    if (confirmRemoval && row != null) AlertDialog(
        onDismissRequest = { confirmRemoval = false },
        title = { Text("Remove row ${row.label}?") },
        text = { Text("This removes ${row.seats.size} seats from the draft layout.") },
        confirmButton = { TextButton({ confirmRemoval = false; onAction(VentAction.RemoveEditingRow) }) { Text("Remove row", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmRemoval = false }) { Text("Cancel") } },
    )
}

@Composable
private fun SeatLegend() {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
    ) {
        LegendItem("Available", MaterialTheme.colorScheme.surface, true)
        LegendItem("Selected", MaterialTheme.colorScheme.primary)
        LegendItem("Reserved", MaterialTheme.colorScheme.tertiaryContainer)
        LegendItem("Unavailable", MaterialTheme.colorScheme.surfaceContainerHighest)
    }
}

@Composable
private fun LegendItem(label: String, color: androidx.compose.ui.graphics.Color, outlined: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            Modifier.size(MaterialTheme.spacing.lg),
            shape = MaterialTheme.shapes.extraSmall,
            color = color,
            border = if (outlined) BorderStroke(MaterialTheme.spacing.xs / 4, MaterialTheme.colorScheme.outline) else null,
        ) {}
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
