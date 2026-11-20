package app.vent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ImageCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import app.vent.ui.theme.spacing
import app.vent.ui.theme.EventIcons
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class MapSeat(val seat: VenueSeat, val center: Offset)

internal fun nearestMapSeat(seats: List<MapSeat>, point: Offset, radius: Float): MapSeat? =
    seats.minByOrNull { (it.center - point).getDistanceSquared() }
        ?.takeIf { (it.center - point).getDistance() <= radius }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CanvasVenueMap(ui: VentUiState, locateRequest: Int, onSeatClick: (String) -> Unit, modifier: Modifier) {
    val spacing = MaterialTheme.spacing
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val colors = MaterialTheme.colorScheme
    if (ui.venueRows.all { it.seats.isEmpty() }) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No seats published for this hall.") }
        return
    }
    val measurer = rememberTextMeasurer()
    val person = painterResource(EventIcons.person)
    val lock = painterResource(EventIcons.lock)
    val star = painterResource(EventIcons.starVip)
    val warning = painterResource(EventIcons.warning)
    val accessible = painterResource(EventIcons.accessible)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onSurface)
    val step = with(density) { spacing.touch.toPx() }
    val visual = with(density) { spacing.huge.toPx() }
    val gap = with(density) { spacing.sm.toPx() }
    val stroke = with(density) { (spacing.xs / 4).toPx() }
    val radius = with(density) { (spacing.touch / 2 - spacing.xs / 2).toPx() }
    val assigned = ui.seatAssignments[ui.session].orEmpty().values.toSet()
    val seats = remember(ui.venueRows, ui.seatLayoutMode, assigned, step) {
        ui.venueRows.flatMapIndexed { rowIndex, row ->
            row.seats.mapIndexed { index, original ->
                val seat = if (original.label in assigned && original.availability != SeatAvailability.BLOCKED)
                    original.copy(availability = SeatAvailability.BOOKED) else original
                val midpoint = row.seats.size / 2
                val curve = if (ui.seatLayoutMode == SeatLayoutMode.CURVED)
                    (midpoint - abs(index - (midpoint - .5f))).coerceAtLeast(0f) * gap else 0f
                MapSeat(seat, Offset(
                    step * (index + 1.5f) + if (index >= midpoint) step else 0f,
                    step * (rowIndex + 2.5f) + curve,
                ))
            }
        }
    }
    val mapSize = Size(
        step * ((ui.venueRows.maxOfOrNull { it.seats.size } ?: 1) + 3),
        step * (ui.venueRows.size + 5),
    )
    val staticLayer = remember(mapSize, colors, labelStyle, measurer, density, direction, ui.venueRows.map { it.label }) {
        val cacheScale = minOf(1f, 2048f / mapSize.width, 2048f / mapSize.height)
        val bitmap = ImageBitmap((mapSize.width * cacheScale).roundToInt().coerceAtLeast(1),
            (mapSize.height * cacheScale).roundToInt().coerceAtLeast(1))
        val canvas = ImageCanvas(bitmap)
        canvas.scale(cacheScale, cacheScale)
        CanvasDrawScope().draw(density, direction, canvas, mapSize) {
            drawRect(colors.surface)
            drawRoundRect(colors.surfaceContainerHigh, Offset(step, step / 2),
                Size(mapSize.width - step * 2, step), CornerRadius(gap))
            val stage = measurer.measure("STAGE", labelStyle)
            drawText(stage, topLeft = Offset((mapSize.width - stage.size.width) / 2, step - stage.size.height / 2))
            ui.venueRows.forEachIndexed { index, row ->
                val text = measurer.measure(row.label, labelStyle)
                drawText(text, topLeft = Offset(step / 4, step * (index + 2.5f) - text.size.height / 2))
            }
            drawText(measurer.measure("ENTRANCE", labelStyle), topLeft = Offset(step, mapSize.height - step))
            val exit = measurer.measure("EXIT", labelStyle)
            drawText(exit, topLeft = Offset(mapSize.width - step - exit.size.width, mapSize.height - step))
        }
        bitmap
    }
    var zoom by rememberSaveable(ui.session) { mutableStateOf(1f) }
    var pan by remember(ui.session) { mutableStateOf(Offset.Zero) }
    var seatList by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val viewport = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
            val fit = minOf(viewport.width / mapSize.width, viewport.height / mapSize.height).coerceAtLeast(.01f)
            val scale = fit * zoom
            val origin = Offset((viewport.width - mapSize.width * scale) / 2, (viewport.height - mapSize.height * scale) / 2) + pan
            LaunchedEffect(locateRequest, ui.session, viewport) {
                if (locateRequest > 0 && ui.role == DemoRole.ATTENDEE) {
                    seats.firstOrNull { it.seat.label == ui.confirmedSeat }?.let { target ->
                        zoom = 3f
                        val locatedScale = fit * zoom
                        val centered = Offset((viewport.width - mapSize.width * locatedScale) / 2,
                            (viewport.height - mapSize.height * locatedScale) / 2)
                        pan = Offset(viewport.width / 2, viewport.height / 2) - target.center * locatedScale - centered
                    }
                } else { zoom = 1f; pan = Offset.Zero }
            }
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Seat map. Pinch to zoom or use Choose seat by label." }
                .pointerInput(seats, scale, origin, ui.role, ui.seatSelectionOpen) {
                    detectTapGestures { point ->
                        nearestMapSeat(seats, (point - origin) / scale, radius / scale)
                            ?.takeIf { ui.role == DemoRole.ORGANIZER ||
                                (ui.seatSelectionOpen && it.seat.availability == SeatAvailability.AVAILABLE) }
                            ?.let { onSeatClick(it.seat.label) }
                    }
                }
                .pointerInput(fit, viewport, mapSize) {
                    detectTransformGestures { centroid, movement, magnification, _ ->
                        val previousScale = fit * zoom
                        val previousOrigin = Offset((viewport.width - mapSize.width * previousScale) / 2,
                            (viewport.height - mapSize.height * previousScale) / 2) + pan
                        val anchor = (centroid - previousOrigin) / previousScale
                        zoom = (zoom * magnification).coerceIn(1f, 3f)
                        val nextScale = fit * zoom
                        val centered = Offset((viewport.width - mapSize.width * nextScale) / 2,
                            (viewport.height - mapSize.height * nextScale) / 2)
                        val nextPan = centroid + movement - anchor * nextScale - centered
                        pan = Offset(nextPan.x.coerceIn(-mapSize.width * nextScale / 2, mapSize.width * nextScale / 2),
                            nextPan.y.coerceIn(-mapSize.height * nextScale / 2, mapSize.height * nextScale / 2))
                    }
                }) {
                withTransform({ translate(origin.x, origin.y); scale(scale, scale, Offset.Zero) }) {
                    drawImage(staticLayer, dstSize = IntSize(mapSize.width.roundToInt(), mapSize.height.roundToInt()))
                    seats.forEach { entry ->
                        val seat = entry.seat
                        val yours = seat.label == ui.confirmedSeat && ui.role == DemoRole.ATTENDEE
                        val selected = !yours && seat.label == ui.selectedSeat && ui.role == DemoRole.ATTENDEE
                        val blocked = seat.availability == SeatAvailability.BLOCKED
                        val taken = seat.availability == SeatAvailability.BOOKED || seat.availability == SeatAvailability.RESERVED
                        val vip = seat.tier == SeatTier.VIP || seat.type == SeatType.VIP
                        val fill = when {
                            yours -> colors.primary
                            selected -> colors.secondary
                            taken -> colors.surfaceContainerHigh
                            vip -> colors.tertiaryContainer
                            else -> colors.surface
                        }
                        val ink = when {
                            yours -> colors.onPrimary
                            selected -> colors.onSecondary
                            blocked -> colors.error
                            else -> colors.onSurface
                        }
                        val corner = entry.center - Offset(visual / 2, visual / 2)
                        drawRoundRect(fill, corner, Size(visual, visual), CornerRadius(gap))
                        drawRoundRect(if (blocked) colors.error else if (vip) colors.tertiary else colors.outline,
                            corner, Size(visual, visual), CornerRadius(gap),
                            style = Stroke(stroke, pathEffect = if (blocked) PathEffect.dashPathEffect(floatArrayOf(gap / 2, gap / 2)) else null))
                        val icon = when {
                            yours -> person
                            taken -> lock
                            blocked -> warning
                            seat.type == SeatType.ACCESSIBLE -> accessible
                            vip -> star
                            else -> null
                        }
                        if (icon != null) {
                            val iconSize = visual * .6f
                            withTransform({ translate(entry.center.x - iconSize / 2, entry.center.y - iconSize / 2) }) {
                                with(icon) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(ink)) }
                            }
                        }
                        if (zoom > 1.5f) {
                            val text = measurer.measure(seat.label, labelStyle.copy(color = ink))
                            drawText(text, topLeft = Offset(entry.center.x - text.size.width / 2f, corner.y + visual + stroke))
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(spacing.sm)) {
                FilledTonalButton({ zoom = (zoom - .5f).coerceAtLeast(1f) }, enabled = zoom > 1f,
                    contentPadding = PaddingValues(spacing.sm)) { Text("−", Modifier.semantics { contentDescription = "Zoom out" }) }
                FilledTonalButton({ zoom = (zoom + .5f).coerceAtMost(3f) }, enabled = zoom < 3f,
                    contentPadding = PaddingValues(spacing.sm)) { Text("+", Modifier.semantics { contentDescription = "Zoom in" }) }
            }
        }
        OutlinedButton({ seatList = true }, Modifier.fillMaxWidth().heightIn(min = spacing.touch)) { Text("Choose seat by label") }
    }
    if (seatList) ModalBottomSheet(onDismissRequest = { seatList = false }) {
        Text("Seats", Modifier.padding(spacing.lg), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = spacing.touch * 8)) {
            items(seats, key = { it.seat.label }) { entry ->
                val seat = entry.seat
                val enabled = ui.role == DemoRole.ORGANIZER || (ui.seatSelectionOpen && seat.availability == SeatAvailability.AVAILABLE)
                OutlinedButton({ onSeatClick(seat.label); seatList = false }, Modifier.fillMaxWidth().heightIn(min = spacing.touch),
                    enabled = enabled) {
                    Text("${seat.label} · ${seat.type.name.lowercase()} · ${seat.availability.name.lowercase()}")
                }
            }
        }
    }
}
