package app.vent

import app.vent.data.rememberVentLocalStore
import app.vent.data.createVentApi
import app.vent.data.SyncingVentLocalStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import app.vent.ui.theme.VentTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import app.vent.ui.theme.spacing
import app.vent.ui.theme.success
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable internal sealed interface VentRoute : NavKey
@Serializable internal data object EventRoute : VentRoute
@Serializable internal data object ScheduleRoute : VentRoute
@Serializable internal data object SeatRoute : VentRoute
@Serializable internal data object QuestsRoute : VentRoute
@Serializable internal data object ProfileRoute : VentRoute
@Serializable internal data object OrganizerRoute : VentRoute
@Serializable internal data object PeopleRoute : VentRoute
@Serializable internal data object ScannerRoute : VentRoute
@Serializable internal data object WrappedRoute : VentRoute
@Serializable internal data object DoorRoute : VentRoute
@Serializable internal data object OccupancyRoute : VentRoute
@Serializable internal data object ControlsRoute : VentRoute
@Serializable internal data object SetupRoute : VentRoute
@Serializable internal data object ProjectorRoute : VentRoute
@Serializable internal data object ConfirmedRoute : VentRoute
@Serializable internal data object DuplicateRoute : VentRoute
@Serializable internal data object WrongRoute : VentRoute
@Serializable internal data object ExpiredRoute : VentRoute

private val ventNavigationConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(EventRoute::class, EventRoute.serializer())
            subclass(ScheduleRoute::class, ScheduleRoute.serializer())
            subclass(SeatRoute::class, SeatRoute.serializer())
            subclass(QuestsRoute::class, QuestsRoute.serializer())
            subclass(ProfileRoute::class, ProfileRoute.serializer())
            subclass(OrganizerRoute::class, OrganizerRoute.serializer())
            subclass(PeopleRoute::class, PeopleRoute.serializer())
            subclass(ScannerRoute::class, ScannerRoute.serializer())
            subclass(WrappedRoute::class, WrappedRoute.serializer())
            subclass(DoorRoute::class, DoorRoute.serializer())
            subclass(OccupancyRoute::class, OccupancyRoute.serializer())
            subclass(ControlsRoute::class, ControlsRoute.serializer())
            subclass(SetupRoute::class, SetupRoute.serializer())
            subclass(ProjectorRoute::class, ProjectorRoute.serializer())
            subclass(ConfirmedRoute::class, ConfirmedRoute.serializer())
            subclass(DuplicateRoute::class, DuplicateRoute.serializer())
            subclass(WrongRoute::class, WrongRoute.serializer())
            subclass(ExpiredRoute::class, ExpiredRoute.serializer())
        }
    }
}

data class NavItem(val screen: VentScreen, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
fun VentApp(allowLocalOrganizer: Boolean = false) {
    VentTheme(darkTheme = true) {
        val localStore = rememberVentLocalStore()
        val authViewModel = viewModel { AuthViewModel(createVentApi(), localStore, allowLocalOrganizer) }
        val api = authViewModel.api
        val authState by authViewModel.state.collectAsState()
        if (authState.step != AuthStep.AUTHENTICATED) {
            val submitEmail = if (allowLocalOrganizer) authViewModel::signInLocally else authViewModel::requestCode
            AuthFlow(authState, authViewModel::updateEmail, authViewModel::updateCode, authViewModel::requestCode, authViewModel::verify, authViewModel::changeEmail, authViewModel::chooseEvent, submitEmail)
            return@VentTheme
        }
        val account = if (authState.localAttendee) "" else authState.email.trim().lowercase()
        val accountStore = rememberVentLocalStore(account)
        val syncStore = remember(accountStore, api) {
            if (account.isEmpty()) null else SyncingVentLocalStore(accountStore, api)
        }
        val ventViewModel = viewModel(key = "vent:${authState.signInGeneration}:${authState.organizer}:${authState.email}:${authState.localAttendee}") {
            VentViewModel(syncStore ?: accountStore, attendeeOnly = !authState.organizer, startAsOrganizer = authState.organizer, accountEmail = authState.email)
        }
        LaunchedEffect(ventViewModel) {
            if (account.isEmpty()) return@LaunchedEffect
            while (true) {
                try {
                    ventViewModel.syncPreferences()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep local changes pending until the API is reachable.
                }
                delay(15_000)
            }
        }
        val uiState by ventViewModel.uiState.collectAsState()
        val onAction = ventViewModel::onAction
        val attendeeItems = listOf(
            NavItem(VentScreen.SCHEDULE, "Sessions", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
            NavItem(VentScreen.SEAT, "Seat Map", Icons.Outlined.Chair, Icons.Filled.Chair),
            NavItem(VentScreen.QUESTS, "Quests", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents),
            NavItem(VentScreen.PROFILE, "Summit Pass", Icons.Outlined.Badge, Icons.Filled.Badge),
        )
        val organizerItems = listOf(
            NavItem(VentScreen.ORGANIZER, "Events", Icons.Outlined.Event, Icons.Filled.Event),
            NavItem(VentScreen.SCHEDULE, "Sessions", Icons.Outlined.ViewTimeline, Icons.Filled.ViewTimeline),
            NavItem(VentScreen.QUESTS, "Quests", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents),
            NavItem(VentScreen.PROFILE, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
        )
        val items = if (uiState.role == DemoRole.ATTENDEE) attendeeItems else organizerItems
        val inOperations = uiState.role == DemoRole.ORGANIZER &&
            uiState.screen in setOf(VentScreen.DOOR, VentScreen.OCCUPANCY, VentScreen.CONTROLS)
        VentTheme(accent = uiState.eventAccent?.let { androidx.compose.ui.graphics.Color(it) }) {
        ProvideAttendeePass(api, authState.events.firstOrNull()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 720.dp
            if (expanded) {
                Row(Modifier.fillMaxSize()) {
                    if (!inOperations) VentRail(items,uiState.screen,{onAction(VentAction.Navigate(it))},uiState.role)
                    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface) {
                        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
                            Box(Modifier.fillMaxHeight().fillMaxWidth().widthIn(max=1200.dp)){VentSurface(uiState,onAction,true,authState.email,authViewModel::signOut)}
                        }
                    }
                }
            } else {
                Scaffold(
                    containerColor=MaterialTheme.colorScheme.surface,
                    bottomBar = {
                        if (uiState.role == DemoRole.ATTENDEE && uiState.screen != VentScreen.SCANNER) {
                            VentBottomBar(items, uiState.screen) { onAction(VentAction.Navigate(it)) }
                        } else if (!inOperations && uiState.screen != VentScreen.SCANNER) {
                            val selected = if (uiState.screen == VentScreen.SETUP) VentScreen.PROFILE else uiState.screen
                            VentBottomBar(items, selected) { onAction(VentAction.Navigate(it)) }
                        }
                    },
                ) { padding -> Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { VentSurface(uiState,onAction,false,authState.email,authViewModel::signOut) } }
            }
        }
        SessionReminderHost(uiState, onAction)
        }
        }
    }
}

@Composable private fun VentSurface(uiState:VentUiState,onAction:(VentAction)->Unit,expanded:Boolean,email:String,onSignOut:()->Unit){
    val target = uiState.screen.toRoute()
    val backStack = rememberNavBackStack(ventNavigationConfig, target)
    BindBrowserNavigation(backStack)
    LaunchedEffect(target) {
        if (backStack.lastOrNull() != target) backStack.add(target)
    }
    LaunchedEffect(uiState.role) {
        if (backStack.lastOrNull()?.toScreen()?.let { it != uiState.screen } == true) {
            backStack.clear()
            backStack.add(target)
        }
    }
    LaunchedEffect(backStack.lastOrNull()) {
        backStack.lastOrNull()?.toScreen()?.takeIf { it != uiState.screen }?.let {
            onAction(VentAction.Navigate(it))
        }
    }
    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        },
        entryProvider = entryProvider {
            entry<EventRoute> { EventHome(uiState,onAction,expanded) }
            entry<ScheduleRoute> { SessionsHome(uiState, onAction) }
            entry<SeatRoute> { SeatSelectionScreen(uiState,onAction) }
            entry<QuestsRoute> { SummitScreen(VentScreen.QUESTS, uiState, onAction, email) }
            entry<ProfileRoute> {
                if (uiState.role == DemoRole.ORGANIZER) {
                    OrganizerOperations(VentScreen.SETUP, uiState, onAction, onSignOut)
                } else SummitScreen(VentScreen.PROFILE, uiState, onAction, email, onSignOut)
            }
            entry<OrganizerRoute> { OrganizerDashboard(uiState, expanded, onAction) }
            entry<PeopleRoute> { AttendeeImportScreen(uiState.attendees, onAction) }
            entry<ScannerRoute> { ScanEntry(uiState, onAction) }
            entry<WrappedRoute> { SummitScreen(VentScreen.WRAPPED, uiState, onAction) }
            entry<DoorRoute> { OrganizerOperations(VentScreen.DOOR, uiState, onAction) }
            entry<OccupancyRoute> { OrganizerOperations(VentScreen.OCCUPANCY, uiState, onAction) }
            entry<ControlsRoute> { OrganizerOperations(VentScreen.CONTROLS, uiState, onAction) }
            entry<SetupRoute> { OrganizerOperations(VentScreen.SETUP, uiState, onAction, onSignOut) }
            entry<ProjectorRoute> {
                if (expanded) SummitScreen(VentScreen.PROJECTOR, uiState, onAction)
                else LaunchedEffect(Unit) { onAction(VentAction.Navigate(VentScreen.ORGANIZER)) }
            }
            entry<ConfirmedRoute> { SummitScreen(VentScreen.CONFIRMED, uiState, onAction) }
            entry<DuplicateRoute> { SummitScreen(VentScreen.DUPLICATE, uiState, onAction) }
            entry<WrongRoute> { SummitScreen(VentScreen.WRONG, uiState, onAction) }
            entry<ExpiredRoute> { SummitScreen(VentScreen.EXPIRED, uiState, onAction) }
        },
    )
}

private fun VentScreen.toRoute(): VentRoute = when (this) {
    VentScreen.EVENT -> EventRoute
    VentScreen.SCHEDULE -> ScheduleRoute
    VentScreen.SEAT -> SeatRoute
    VentScreen.QUESTS -> QuestsRoute
    VentScreen.PROFILE -> ProfileRoute
    VentScreen.ORGANIZER -> OrganizerRoute
    VentScreen.PEOPLE -> PeopleRoute
    VentScreen.SCANNER -> ScannerRoute
    VentScreen.WRAPPED -> WrappedRoute
    VentScreen.DOOR -> DoorRoute
    VentScreen.OCCUPANCY -> OccupancyRoute
    VentScreen.CONTROLS -> ControlsRoute
    VentScreen.SETUP -> SetupRoute
    VentScreen.PROJECTOR -> ProjectorRoute
    VentScreen.CONFIRMED -> ConfirmedRoute
    VentScreen.DUPLICATE -> DuplicateRoute
    VentScreen.WRONG -> WrongRoute
    VentScreen.EXPIRED -> ExpiredRoute
}

internal fun NavKey.toScreen(): VentScreen = when (this) {
    EventRoute -> VentScreen.EVENT
    ScheduleRoute -> VentScreen.SCHEDULE
    SeatRoute -> VentScreen.SEAT
    QuestsRoute -> VentScreen.QUESTS
    ProfileRoute -> VentScreen.PROFILE
    OrganizerRoute -> VentScreen.ORGANIZER
    PeopleRoute -> VentScreen.PEOPLE
    ScannerRoute -> VentScreen.SCANNER
    WrappedRoute -> VentScreen.WRAPPED
    DoorRoute -> VentScreen.DOOR
    OccupancyRoute -> VentScreen.OCCUPANCY
    ControlsRoute -> VentScreen.CONTROLS
    SetupRoute -> VentScreen.SETUP
    ProjectorRoute -> VentScreen.PROJECTOR
    ConfirmedRoute -> VentScreen.CONFIRMED
    DuplicateRoute -> VentScreen.DUPLICATE
    WrongRoute -> VentScreen.WRONG
    ExpiredRoute -> VentScreen.EXPIRED
    else -> error("Unknown Vent navigation route: $this")
}

@Composable internal fun Page(title:String,modifier:Modifier=Modifier,actions:@Composable RowScope.()->Unit={},content:@Composable ColumnScope.()->Unit){
    Surface(modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface){
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))){
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = actions)
            Column(Modifier.fillMaxSize(),content=content)
        }
    }
}

@Composable private fun EventHome(uiState:VentUiState,onAction:(VentAction)->Unit,expanded:Boolean){ EventDetailScreen(uiState, onAction, expanded) }

@Composable
private fun CheckInBottomBar(items: List<NavItem>, selected: VentScreen, navigate: (VentScreen) -> Unit) {
    val space = MaterialTheme.spacing
    val barColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val cutoutColor = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.padding(top = space.md).fillMaxWidth().height(space.heroIcon)
                .drawBehind {
                    drawRect(barColor)
                    drawCircle(cutoutColor, radius = (space.touch / 2 + space.xs).toPx(),
                        center = Offset(size.width / 2, (space.touch / 2 - space.md).toPx()))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                if (index == items.size / 2) Spacer(Modifier.width(space.action + space.sm))
                NavigationBarItem(
                    selected = selected == item.screen,
                    onClick = { navigate(item.screen) },
                    icon = { Icon(if (selected == item.screen) item.selectedIcon else item.icon, item.label) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
        SmallFloatingActionButton(
            onClick = { navigate(VentScreen.SCANNER) },
            modifier = Modifier.size(space.touch),
            shape = androidx.compose.foundation.shape.CircleShape,
        ) {
            Icon(Icons.Outlined.QrCodeScanner, "Scan to check in")
        }
    }
}

@Composable
internal fun Modifier.conferencePattern(): Modifier {
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val spacing = MaterialTheme.spacing.xxl
    return drawBehind {
        val interval = spacing.toPx()
        var x = interval
        while (x < size.width + size.height) {
            drawLine(accent, Offset(x, 0f), Offset(x - size.height, size.height))
            x += interval
        }
    }
}

@Composable
private fun OrganizerDashboard(ui: VentUiState, expanded: Boolean, dispatch: (VentAction) -> Unit) {
    val space = MaterialTheme.spacing
    fun open(screen: VentScreen) = dispatch(VentAction.Navigate(screen))
    Box(Modifier.fillMaxSize()) {
        Page(ui.eventTitle, actions = {
            TextButton({ open(VentScreen.SETUP) }) { Text("Set up") }
        }) {
            LazyColumn(contentPadding = PaddingValues(space.lg), verticalArrangement = Arrangement.spacedBy(space.md)) {
                item {
                    val sessions = (SummitSession.entries + ui.sessionDetails.keys).distinct()
                    val session = sessions.firstOrNull { ui.sessionDetails[it]?.phase == SessionPhase.LIVE }
                        ?: sessions.firstOrNull { (ui.sessionDetails[it]?.phase ?: SessionPhase.UPCOMING) == SessionPhase.UPCOMING }
                        ?: ui.session
                    val details = ui.sessionDetails[session] ?: SessionDetails(session.title, session.time, session.room, session.speaker)
                    OutlinedCard(Modifier.fillMaxWidth().heightIn(min = space.huge * 4 + space.xl)) {
                        Column(Modifier.padding(space.md), verticalArrangement = Arrangement.spacedBy(space.sm)) {
                            SessionStatus(details.phase)
                            Text(details.title, style = MaterialTheme.typography.titleLarge)
                            Text(details.time + " · " + details.room, style = MaterialTheme.typography.labelLarge)
                            Button({
                                dispatch(VentAction.OpenSession(session))
                                if (details.phase == SessionPhase.UPCOMING) dispatch(VentAction.SetSessionPhase(session, SessionPhase.LIVE))
                                else open(VentScreen.CONTROLS)
                            }, Modifier.fillMaxWidth().height(space.action)) {
                                Text(if (details.phase == SessionPhase.UPCOMING) "Start" else "Open live controls")
                            }
                        }
                    }
                }
                item {
                    Text("Today", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
                        OrganizerStatusTile("Checked in", ui.checkIns[ui.session].orEmpty().size,
                            Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.success, Modifier.weight(1f))
                        OrganizerStatusTile("Assigned", ui.seatAssignments[ui.session].orEmpty().size,
                            Icons.Outlined.Chair, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                        val blocked = ui.venueRows.sumOf { row -> row.seats.count { it.availability == SeatAvailability.BLOCKED } }
                        OrganizerStatusTile("Blocked", blocked, Icons.Outlined.WarningAmber,
                            if (blocked > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                    }
                }
                item { Text("Live operations", style = MaterialTheme.typography.titleLarge) }
                val links = listOf(
                    Triple(VentScreen.SCHEDULE, "Sessions and seating", "Review session details and manage seat layouts"),
                    Triple(VentScreen.PEOPLE, "Manage attendees", "Add attendees or upload a CSV"),
                    Triple(VentScreen.OCCUPANCY, "Occupancy", "Review room status"),
                    Triple(VentScreen.CONTROLS, "Live controls", "Manage session operations"),
                ) + if (expanded) listOf(Triple(VentScreen.PROJECTOR, "Projector", "Open the stage display")) else emptyList()
                items(links, key = { it.first.name }) { (screen, title, detail) ->
                    Card(onClick = { open(screen) }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(title) },
                            supportingContent = { Text(detail) },
                            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun OrganizerStatusTile(label: String, value: Int, icon: ImageVector, tint: Color, modifier: Modifier) {
    OutlinedCard(modifier.heightIn(min = MaterialTheme.spacing.touch * 2)) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            Icon(icon, null, tint = tint)
            Text(value.toString(), style = MaterialTheme.typography.displaySmall, color = tint)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable private fun Metric(value:String,label:String,modifier:Modifier=Modifier){Surface(modifier,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.secondaryContainer){Column(Modifier.padding(20.dp)){Text(value,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSecondaryContainer);Text(label,color=MaterialTheme.colorScheme.onSecondaryContainer)}}}

@Composable private fun SectionTitle(title:String,trailing:String){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text(trailing,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}}
@Composable
private fun VentBottomBar(items: List<NavItem>, selected: VentScreen, onSelect: (VentScreen) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
        items.forEach { item ->
            NavigationBarItem(
                selected = selected == item.screen,
                onClick = { onSelect(item.screen) },
                icon = { NavigationGlyph(item) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}
@Composable
private fun NavigationGlyph(item: NavItem) {
    val icons = app.vent.ui.theme.EventIcons
    val resource = when (item.screen) {
        VentScreen.SCHEDULE -> icons.calendar
        VentScreen.SEAT -> icons.seat
        VentScreen.QUESTS -> icons.questTrophy
        VentScreen.PROFILE -> if (item.label == "Settings") icons.settingsGear else icons.pass
        else -> icons.calendar
    }
    Icon(org.jetbrains.compose.resources.painterResource(resource), item.label)
}
@Composable private fun VentRail(items:List<NavItem>,selected:VentScreen,onSelect:(VentScreen)->Unit,role:DemoRole){NavigationRail(header={Column(horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.height(18.dp));VentMark(MaterialTheme.colorScheme.primary, Modifier.size(MaterialTheme.spacing.touch));Spacer(Modifier.height(22.dp))}}){items.forEach{item->NavigationRailItem(selected=selected==item.screen,onClick={onSelect(item.screen)},icon={Icon(item.icon,item.label)},label={Text(item.label)})};Spacer(Modifier.weight(1f));Text(if(role==DemoRole.ORGANIZER)"Organizer" else "Attendee",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(18.dp))}}

