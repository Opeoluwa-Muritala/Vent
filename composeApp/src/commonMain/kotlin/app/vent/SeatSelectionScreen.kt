package app.vent

import androidx.compose.foundation.BorderStroke
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

@Composable
internal fun SeatSelectionScreen(uiState: VentUiState, onAction: (VentAction) -> Unit) {
    Page(if (uiState.role == DemoRole.ORGANIZER) "Seating" else "Seat navigator") {
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

@Composable
private fun SeatMapPane(uiState: VentUiState, onAction: (VentAction) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiState.session.room, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (uiState.role == DemoRole.ORGANIZER) {
                        "${uiState.venueRows.sumOf { it.seats.size }} seats · ${uiState.seatLayoutMode.name.lowercase()} layout"
                    } else {
                        uiState.confirmedSeat.ifBlank { "No seat selected" }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = { onAction(VentAction.LocateConfirmedSeat) }) {
                Icon(Icons.Outlined.MyLocation, contentDescription = null)
                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                Text(if (uiState.role == DemoRole.ORGANIZER) "Fit map" else "Find ${uiState.confirmedSeat}")
            }
        }
        VenueMap(
            uiState = uiState,
            onSeatClick = { label ->
                onAction(
                    if (uiState.role == DemoRole.ORGANIZER) VentAction.ApplySeatEdit(label)
                    else VentAction.SelectSeat(label),
                )
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun VenueMap(uiState: VentUiState, onSeatClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(MaterialTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                Surface(
                    modifier = Modifier.width(MaterialTheme.spacing.giant * 8).heightIn(min = MaterialTheme.spacing.touch),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("STAGE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondary)
                    }
                }
                var previousTier: SeatTier? = null
                uiState.venueRows.forEach { row ->
                    val tier = (row.seats.firstOrNull()?.tier ?: SeatTier.STANDARD)
                    if (tier != previousTier) SectionLabel(
                        if (tier == SeatTier.VIP) "VIP section" else "Standard section",
                        tier == SeatTier.VIP,
                    )
                    SeatRow(row, uiState, onSeatClick)
                    previousTier = tier
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    VenueMarker("Entrance")
                    VenueMarker("Exit")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, vip: Boolean) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (vip) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SeatRow(row: VenueRow, uiState: VentUiState, onSeatClick: (String) -> Unit) {
    val midpoint = row.seats.size / 2
    val curved = uiState.seatLayoutMode == SeatLayoutMode.CURVED
    val density = LocalDensity.current
    val curveStep = with(density) { MaterialTheme.spacing.sm.toPx() }
    Row(
        modifier = Modifier.height(if (curved) MaterialTheme.spacing.touch + MaterialTheme.spacing.xxl else MaterialTheme.spacing.touch),
        verticalAlignment = Alignment.Top,
    ) {
        Text(row.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(MaterialTheme.spacing.touch).padding(top = MaterialTheme.spacing.lg))
        row.seats.take(midpoint).forEachIndexed { index, seat ->
            SeatControl(seat, uiState, onSeatClick, curvedSeatModifier(index, midpoint, curved, curveStep))
        }
        Spacer(Modifier.width(MaterialTheme.spacing.touch))
        row.seats.drop(midpoint).forEachIndexed { index, seat ->
            SeatControl(seat, uiState, onSeatClick, curvedSeatModifier(index + midpoint, midpoint, curved, curveStep))
        }
        Text(row.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(MaterialTheme.spacing.touch).padding(top = MaterialTheme.spacing.lg))
    }
}

private fun curvedSeatModifier(index: Int, midpoint: Int, curved: Boolean, curveStep: Float): Modifier {
    if (!curved) return Modifier
    val distance = abs(index - (midpoint - 0.5f))
    return Modifier.graphicsLayer {
        translationY = (midpoint - distance).coerceAtLeast(0f) * curveStep
        rotationZ = (midpoint - 0.5f - index) * 4f
    }
}

@Composable
private fun SeatControl(seat: VenueSeat, uiState: VentUiState, onSeatClick: (String) -> Unit, modifier: Modifier) {
    val organizer = uiState.role == DemoRole.ORGANIZER
    val available = seat.availability == SeatAvailability.AVAILABLE
    val selected = !organizer && seat.label == uiState.selectedSeat
    val enabled = organizer || (uiState.seatSelectionOpen && available)
    val state = when {
        selected -> "Selected"
        available -> "Available"
        seat.availability == SeatAvailability.RESERVED -> "Reserved"
        seat.availability == SeatAvailability.BOOKED -> "Booked"
        else -> "Blocked"
    }
    Surface(
        onClick = { onSeatClick(seat.label) },
        enabled = enabled,
        modifier = modifier.size(MaterialTheme.spacing.touch).padding(MaterialTheme.spacing.xs).semantics {
            role = Role.Button
            this.selected = selected
            stateDescription = "$state, ${seat.type.name.lowercase()} seat"
            if (!enabled) disabled()
        },
        shape = MaterialTheme.shapes.small,
        color = when {
            selected -> MaterialTheme.colorScheme.primary
            available -> MaterialTheme.colorScheme.surface
            seat.availability == SeatAvailability.RESERVED -> MaterialTheme.colorScheme.tertiaryContainer
            seat.availability == SeatAvailability.BOOKED -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.errorContainer
        },
        contentColor = when {
            selected -> MaterialTheme.colorScheme.onPrimary
            seat.availability == SeatAvailability.RESERVED -> MaterialTheme.colorScheme.onTertiaryContainer
            seat.availability == SeatAvailability.BLOCKED -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        border = if (selected) null else BorderStroke(MaterialTheme.spacing.xs / 4, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(seat.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VenueMarker(label: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xxl, vertical = MaterialTheme.spacing.sm),
        )
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

@Composable
private fun OrganizerSeatControls(uiState: VentUiState, onAction: (VentAction) -> Unit) {
    val selectedRow = uiState.venueRows.firstOrNull { it.label == uiState.editingRowLabel }
    var confirmRowRemoval by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    Text("Edit seating", style = MaterialTheme.typography.titleLarge)
    Text("Choose Block or Unblock, then tap a seat on the map.")
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        listOf(SeatAvailability.BLOCKED to "Block", SeatAvailability.AVAILABLE to "Unblock").forEach { (availability, label) ->
            FilterChip(uiState.seatEditMode == SeatEditMode.AVAILABILITY && uiState.seatEditAvailability == availability,
                {
                    onAction(VentAction.SetSeatEditMode(SeatEditMode.AVAILABILITY))
                    onAction(VentAction.SetSeatEditAvailability(availability))
                }, { Text(label) })
        }
    }
    TextButton({ more = !more }) { Text(if (more) "Fewer options" else "More options") }
    if (more) {
    Text(
        "Choose what to edit, select a value, then tap chairs to apply it.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        SeatEditMode.entries.forEach { mode ->
            FilterChip(
                selected = uiState.seatEditMode == mode,
                onClick = { onAction(VentAction.SetSeatEditMode(mode)) },
                label = { Text(if (mode == SeatEditMode.TYPE) "Seat type" else "Availability") },
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        if (uiState.seatEditMode == SeatEditMode.TYPE) {
            SeatType.entries.forEach { type ->
                FilterChip(
                    selected = uiState.seatEditType == type,
                    onClick = { onAction(VentAction.SetSeatEditType(type)) },
                    label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        } else {
            SeatAvailability.entries.forEach { availability ->
                FilterChip(
                    selected = uiState.seatEditAvailability == availability,
                    onClick = { onAction(VentAction.SetSeatEditAvailability(availability)) },
                    label = { Text(availability.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
    }
    Text("Room layout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        SeatLayoutMode.entries.forEach { mode ->
            FilterChip(
                selected = uiState.seatLayoutMode == mode,
                onClick = { onAction(VentAction.SetSeatLayout(mode)) },
                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
        }
    }
    HorizontalDivider()
    Text("Edit row", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        uiState.venueRows.forEach { row ->
            FilterChip(
                selected = row.label == uiState.editingRowLabel,
                onClick = { onAction(VentAction.SelectEditingRow(row.label)) },
                label = { Text("Row ${row.label}") },
            )
        }
    }
    selectedRow?.let { row ->
        val rowIndex = uiState.venueRows.indexOf(row)
        val canRemoveSeat = row.seats.size > MIN_SEATS_PER_ROW && row.seats.last().label != uiState.confirmedSeat
        val canRemoveRow = uiState.venueRows.size > 1 && row.seats.none { it.label == uiState.confirmedSeat }
        Text(
            "Row ${row.label} · ${row.seats.size} seats · ${(row.seats.firstOrNull()?.tier ?: SeatTier.STANDARD).name.lowercase()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            FilledTonalButton(
                onClick = { onAction(VentAction.RemoveSeatFromRow) },
                enabled = canRemoveSeat,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = null)
                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                Text("Remove seat")
            }
            FilledTonalButton(
                onClick = { onAction(VentAction.AddSeatToRow) },
                enabled = row.seats.size < MAX_SEATS_PER_ROW,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                Text("Add seat")
            }
        }
        FilledTonalButton(onClick = { onAction(VentAction.ToggleEditingRowTier) }, modifier = Modifier.fillMaxWidth()) {
            Text(if ((row.seats.firstOrNull()?.tier ?: SeatTier.STANDARD) == SeatTier.VIP) "Change to standard" else "Change to VIP")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            OutlinedButton(
                onClick = { onAction(VentAction.MoveEditingRow(-1)) },
                enabled = rowIndex > 0,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = null)
                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                Text("Move up")
            }
            OutlinedButton(
                onClick = { onAction(VentAction.MoveEditingRow(1)) },
                enabled = rowIndex < uiState.venueRows.lastIndex,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = null)
                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                Text("Move down")
            }
        }
        TextButton(onClick = { confirmRowRemoval = true }, enabled = canRemoveRow, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
            Text("Remove row", color = MaterialTheme.colorScheme.error)
        }
        if (confirmRowRemoval) {
            AlertDialog(
                onDismissRequest = { confirmRowRemoval = false },
                title = { Text("Remove row ${row.label}?") },
                text = { Text("This removes ${row.seats.size} seats from the draft layout.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRowRemoval = false
                        onAction(VentAction.RemoveEditingRow)
                    }) { Text("Remove row") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRowRemoval = false }) { Text("Cancel") }
                },
            )
        }
    }
    FilledTonalButton(
        onClick = { onAction(VentAction.AddSeatRow) },
        enabled = uiState.venueRows.size < MAX_VENUE_ROWS,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Text("Add seat row")
    }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Attendee selection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (uiState.seatSelectionOpen) "Attendees can change seats" else "Attendee seat changes are closed",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = uiState.seatSelectionOpen,
            onCheckedChange = { onAction(VentAction.SetSeatSelectionOpen(it)) },
        )
    }
    }
    Text(
        if (uiState.layoutPublication == LayoutPublication.PUBLISHED) "Published layout" else "Unpublished changes",
        style = MaterialTheme.typography.labelLarge,
        color = if (uiState.layoutPublication == LayoutPublication.PUBLISHED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        OutlinedButton(onClick = { onAction(VentAction.SaveLayoutDraft) }, modifier = Modifier.weight(1f)) {
            Text("Save draft")
        }
        Button(onClick = { onAction(VentAction.PublishLayout) }, modifier = Modifier.weight(1f)) {
            Text("Publish layout")
        }
    }
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
