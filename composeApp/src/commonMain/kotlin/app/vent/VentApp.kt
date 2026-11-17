package app.vent

import app.vent.data.rememberVentLocalStore
import app.vent.data.createVentApi
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val ventNavigationConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(EventRoute::class, EventRoute.serializer())
            subclass(ScheduleRoute::class, ScheduleRoute.serializer())
            subclass(SeatRoute::class, SeatRoute.serializer())
            subclass(QuestsRoute::class, QuestsRoute.serializer())
            subclass(ProfileRoute::class, ProfileRoute.serializer())
            subclass(OrganizerRoute::class, OrganizerRoute.serializer())
        }
    }
}

data class NavItem(val screen: VentScreen, val label: String, val icon: ImageVector)

@Composable
fun VentApp() {
    VentTheme {
        val authViewModel = viewModel { AuthViewModel(createVentApi()) }
        val authState by authViewModel.state.collectAsState()
        if (authState.step != AuthStep.AUTHENTICATED) {
            AuthFlow(authState, authViewModel::updateEmail, authViewModel::updateCode, authViewModel::requestCode, authViewModel::verify, authViewModel::changeEmail, authViewModel::chooseEvent)
            return@VentTheme
        }
        val localStore = rememberVentLocalStore()
        val ventViewModel = viewModel { VentViewModel(localStore) }
        val uiState by ventViewModel.uiState.collectAsState()
        val onAction = ventViewModel::onAction
        val attendeeItems = listOf(
            NavItem(VentScreen.EVENT,"Event",Icons.Outlined.Home),
            NavItem(VentScreen.SCHEDULE,"Schedule",Icons.Outlined.CalendarMonth),
            NavItem(VentScreen.QUESTS,"Quests",Icons.Outlined.MilitaryTech),
            NavItem(VentScreen.PROFILE,"Profile",Icons.Outlined.Person),
        )
        val organizerItems = listOf(
            NavItem(VentScreen.ORGANIZER,"Overview",Icons.Outlined.Dashboard),
            NavItem(VentScreen.SCHEDULE,"Sessions",Icons.Outlined.CalendarMonth),
            NavItem(VentScreen.SEAT,"Seating",Icons.Outlined.Chair),
            NavItem(VentScreen.PROFILE,"Profile",Icons.Outlined.Person),
        )
        val items = if (uiState.role == DemoRole.ATTENDEE) attendeeItems else organizerItems
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 720.dp
            if (expanded) {
                Row(Modifier.fillMaxSize()) {
                    VentRail(items,uiState.screen,{onAction(VentAction.Navigate(it))},uiState.role)
                    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface) {
                        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
                            Box(Modifier.fillMaxHeight().fillMaxWidth().widthIn(max=1200.dp)){VentSurface(uiState,onAction,true,authState.email,authViewModel::signOut)}
                        }
                    }
                }
            } else {
                Scaffold(
                    containerColor=MaterialTheme.colorScheme.surface,
                    bottomBar={ VentBottomBar(items,uiState.screen){onAction(VentAction.Navigate(it))} },
                ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { VentSurface(uiState,onAction,false,authState.email,authViewModel::signOut) } }
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
            entry<ScheduleRoute> { ScheduleScreen({onAction(VentAction.Navigate(it))},uiState.role) }
            entry<SeatRoute> { SeatSelectionScreen(uiState,onAction) }
            entry<QuestsRoute> { QuestScreen() }
            entry<ProfileRoute> { ProfileScreen(uiState,email,onSignOut) }
            entry<OrganizerRoute> { OrganizerDashboard({onAction(VentAction.Navigate(it))},expanded) }
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
}

internal fun NavKey.toScreen(): VentScreen = when (this) {
    EventRoute -> VentScreen.EVENT
    ScheduleRoute -> VentScreen.SCHEDULE
    SeatRoute -> VentScreen.SEAT
    QuestsRoute -> VentScreen.QUESTS
    ProfileRoute -> VentScreen.PROFILE
    OrganizerRoute -> VentScreen.ORGANIZER
    else -> error("Unknown Vent navigation route: $this")
}

@Composable internal fun Page(title:String,modifier:Modifier=Modifier,actions:@Composable RowScope.()->Unit={},content:@Composable ColumnScope.()->Unit){
    Surface(modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface){
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))){
            Row(Modifier.fillMaxWidth().heightIn(min=80.dp).padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically){
                Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));actions()
            }
            Column(Modifier.fillMaxSize(),content=content)
        }
    }
}

@Composable private fun EventHome(uiState:VentUiState,onAction:(VentAction)->Unit,expanded:Boolean){
    Page("Vent Demo Day",actions={IconButton(onClick={}){Icon(Icons.Outlined.Notifications,"Notifications")}}){
        LazyColumn(contentPadding=PaddingValues(horizontal=24.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
            item { Text("Thursday, 20 November · Main theatre",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            uiState.message?.let { item { StatusBanner(it){onAction(VentAction.ClearMessage)} } }
            item { LiveSession(uiState,onAction,expanded) }
            item { SectionTitle("Your day","2 sessions") }
            item { SessionRow("09:30","Opening keynote","Main theatre · Seat ${uiState.confirmedSeat}",true){onAction(VentAction.Navigate(VentScreen.SEAT))} }
            item { SessionRow("13:00","Build lab","Studio hall · Seat C7",false){onAction(VentAction.Navigate(VentScreen.SEAT))} }
            item { SectionTitle("Momentum","55 points") }
            item { QuestProgressRow("Opinion leader","2 of 3 polls",2f/3f,Icons.Outlined.RecordVoiceOver) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable private fun LiveSession(uiState:VentUiState,onAction:(VentAction)->Unit,expanded:Boolean){
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.primaryContainer){
        if(expanded) Row(Modifier.fillMaxWidth().padding(24.dp),horizontalArrangement=Arrangement.spacedBy(28.dp),verticalAlignment=Alignment.CenterVertically){LiveCopy(uiState,onAction,Modifier.weight(1f));MiniVenue(Modifier.width(300.dp).height(180.dp))}
        else Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){LiveCopy(uiState,onAction);MiniVenue(Modifier.fillMaxWidth().height(150.dp))}
    }
}
@Composable private fun LiveCopy(uiState:VentUiState,onAction:(VentAction)->Unit,modifier:Modifier=Modifier){Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).clip(CircleShape).background(if(uiState.isCheckedIn)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary));Spacer(Modifier.width(8.dp));Text(if(uiState.isCheckedIn)"You are checked in" else "Check-in is open",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)}
    Text("Opening keynote",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
    Text("Your seat is ${uiState.confirmedSeat}. Doors close at 10:00.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(onClick={onAction(VentAction.CheckIn)},enabled=!uiState.isCheckedIn){Icon(if(uiState.isCheckedIn)Icons.Outlined.CheckCircle else Icons.Outlined.QrCodeScanner,null);Spacer(Modifier.width(8.dp));Text(if(uiState.isCheckedIn)"Checked in" else "Check in")};OutlinedButton(onClick={onAction(VentAction.Navigate(VentScreen.SEAT))}){Text("Find ${uiState.confirmedSeat}")}}
}}

@Composable private fun MiniVenue(modifier:Modifier){
    val primary=MaterialTheme.colorScheme.primary;val outline=MaterialTheme.colorScheme.outline;val surface=MaterialTheme.colorScheme.surface
    Canvas(modifier.semantics{contentDescription="Preview of the curved theatre showing your seat B4"}){
        drawRoundRect(surface,cornerRadius=androidx.compose.ui.geometry.CornerRadius(18f));drawRoundRect(primary.copy(alpha=.16f),topLeft=Offset(size.width*.16f,size.height*.08f),size=Size(size.width*.68f,size.height*.18f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(12f))
        for(row in 0..3)for(col in 0..7){val angle=(-.44f+col*.125f);val radius=size.width*(.26f+row*.065f);val center=Offset(size.width/2+sin(angle)*radius,size.height*.16f+cos(angle)*radius*.62f);drawCircle(if(row==1&&col==3)primary else outline.copy(alpha=.5f),if(row==1&&col==3)8f else 5.5f,center)}
    }
}

@Composable private fun ScheduleScreen(navigate:(VentScreen)->Unit,role:DemoRole){Page(if(role==DemoRole.ORGANIZER)"Sessions" else "Schedule"){
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{AssistChip(onClick={},label={Text("Thursday · 20 Nov")},leadingIcon={Icon(Icons.Outlined.Today,null)})}
        item{SessionRow("09:30","Opening keynote","Main theatre · 60 min",true){navigate(VentScreen.SEAT)}}
        item{SessionRow("13:00","Build lab","Studio hall · 90 min",false){navigate(VentScreen.SEAT)}}
    }
}}

@Composable private fun SessionRow(time:String,title:String,detail:String,live:Boolean,onClick:()->Unit){
    Surface(shape=RoundedCornerShape(16.dp),color=if(live)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow){
        Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=18.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically){
            Text(time,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.width(72.dp));Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(detail,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(live)Icon(Icons.Outlined.RadioButtonChecked,"Live",tint=MaterialTheme.colorScheme.primary)else Icon(Icons.Outlined.ChevronRight,"Open")
        }
    }
}

@Composable
private fun SeatMapScreen(uiState:VentUiState,onAction:(VentAction)->Unit) {
    Page(if(uiState.role==DemoRole.ORGANIZER)"Seating" else "Choose your seat"){
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=8.dp)){
            val twoPane=maxWidth>=720.dp
            if(twoPane){
                Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(20.dp)){
                    Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        SeatMapHeader(uiState){onAction(VentAction.LocateConfirmedSeat)}
                        VenueCanvas(Modifier.fillMaxWidth().weight(1f),uiState.selectedSeat,uiState.role==DemoRole.ATTENDEE){onAction(VentAction.SelectSeat(it))}
                    }
                    SeatSelectionPanel(uiState,onAction,Modifier.widthIn(min=288.dp,max=360.dp).fillMaxHeight())
                }
            }else{
                Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    SeatMapHeader(uiState){onAction(VentAction.LocateConfirmedSeat)}
                    VenueCanvas(Modifier.fillMaxWidth().weight(1f),uiState.selectedSeat,uiState.role==DemoRole.ATTENDEE){onAction(VentAction.SelectSeat(it))}
                    SeatSelectionPanel(uiState,onAction,Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable private fun SeatMapHeader(uiState:VentUiState,onLocate:()->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Main theatre",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(if(uiState.role==DemoRole.ORGANIZER)"38 assigned · 31 checked in" else "Current seat ${uiState.confirmedSeat}",color=MaterialTheme.colorScheme.onSurfaceVariant)};FilledTonalButton(onClick=onLocate){Icon(Icons.Outlined.MyLocation,null);Spacer(Modifier.width(8.dp));Text(if(uiState.role==DemoRole.ORGANIZER)"Fit" else "Find ${uiState.confirmedSeat}")}}}

@Composable private fun SeatSelectionPanel(uiState:VentUiState,onAction:(VentAction)->Unit,modifier:Modifier=Modifier){Surface(modifier,shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text(if(uiState.role==DemoRole.ORGANIZER)"Seat states" else "Selected ${uiState.selectedSeat}",style=MaterialTheme.typography.titleLarge);SeatLegend();if(uiState.role==DemoRole.ATTENDEE){Text(if(uiState.selectedSeat==uiState.confirmedSeat)"Tap an outlined seat to choose a new place." else "${uiState.selectedSeat} is available. Confirm to change from ${uiState.confirmedSeat}.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);if(uiState.selectedSeat==uiState.confirmedSeat){OutlinedButton(onClick={},enabled=false,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Seat ${uiState.confirmedSeat} selected")}}else{Button(onClick={onAction(VentAction.ConfirmSeat)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Confirm seat ${uiState.selectedSeat}")}};uiState.message?.let{Text(it,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}}else{Text("Select a seat on the map to inspect assignment and check-in status.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}

@Composable
private fun VenueCanvas(
    modifier: Modifier,
    selectedSeat: String,
    selectionEnabled: Boolean,
    onSeatSelected: (String) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val availableSeat = MaterialTheme.colorScheme.surface
    val selectedSeatMark = MaterialTheme.colorScheme.onPrimary
    val blocked = MaterialTheme.colorScheme.error

    Surface(modifier, shape = RoundedCornerShape(16.dp), color = surface) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .pointerInput(selectedSeat, selectionEnabled) {
                    if (!selectionEnabled) return@pointerInput
                    detectTapGestures { tap ->
                        var closestSeat: String? = null
                        var closestDistance = 24.dp.toPx()
                        for (row in 0..4) for (column in 0..7) {
                            val occupied = row == 0 && column < 3
                            val blockedSeat = row == 2 && column == 7
                            if (!occupied && !blockedSeat) {
                                val angle = -.48f + column * .137f
                                val radius = size.width * (.3f + row * .085f)
                                val center = Offset(
                                    size.width / 2f + sin(angle) * radius,
                                    size.height * .04f + cos(angle) * radius * .72f,
                                )
                                val distance = (tap - center).getDistance()
                                if (distance <= closestDistance) {
                                    closestDistance = distance
                                    closestSeat = "${('A'.code + row).toChar()}${column + 1}"
                                }
                            }
                        }
                        closestSeat?.let(onSeatSelected)
                    }
                }
                .semantics {
                    contentDescription = "Interactive theatre map. Selected seat is $selectedSeat. Outlined seats are available."
                },
        ) {
            drawRoundRect(
                primary.copy(alpha = .12f),
                topLeft = Offset(size.width * .18f, size.height * .04f),
                size = Size(size.width * .64f, size.height * .1f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
            )
            drawLine(
                outline,
                Offset(size.width * .18f, size.height * .17f),
                Offset(size.width * .82f, size.height * .17f),
                2f,
                StrokeCap.Round,
            )
            for (row in 0..4) for (column in 0..7) {
                val angle = -.48f + column * .137f
                val radius = size.width * (.3f + row * .085f)
                val center = Offset(
                    size.width / 2 + sin(angle) * radius,
                    size.height * .04f + cos(angle) * radius * .72f,
                )
                val label = "${('A'.code + row).toChar()}${column + 1}"
                val selected = label == selectedSeat
                val occupied = row == 0 && column < 3
                val blockedSeat = row == 2 && column == 7
                val fill = when {
                    selected -> primary
                    occupied -> tertiary
                    else -> availableSeat
                }
                drawCircle(if (blockedSeat) Color.Transparent else fill, 13f, center)
                drawCircle(
                    if (blockedSeat) blocked else if (selected) primary else outline,
                    13f,
                    center,
                    style = Stroke(if (blockedSeat) 3f else 2f),
                )
                if (selected) drawCircle(selectedSeatMark, 4f, center)
            }
        }
    }
}

@Composable
private fun SeatLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LegendDot(MaterialTheme.colorScheme.primary, "Selected")
        LegendDot(MaterialTheme.colorScheme.surface, "Available", true)
        LegendDot(MaterialTheme.colorScheme.tertiary, "Occupied")
    }
}
@Composable private fun LegendDot(color:Color,label:String,outlined:Boolean=false){Row(verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(12.dp),shape=CircleShape,color=color,border=if(outlined)androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline)else null){};Spacer(Modifier.width(6.dp));Text(label,style=MaterialTheme.typography.labelMedium)}}

@Composable private fun QuestScreen(){Page("Quests"){
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
        item{Text("Complete event moments to unlock badges and move up the leaderboard.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{QuestProgressRow("Full House","1 of 2 sessions",.5f,Icons.Outlined.EventAvailable)}
        item{QuestProgressRow("Opinion leader","2 of 3 polls",2f/3f,Icons.Outlined.RecordVoiceOver)}
        item{QuestProgressRow("Early Bird","Completed",1f,Icons.Outlined.WbTwilight)}
        item{SectionTitle("Leaderboard","You are #2")}
        items(listOf("1" to "Amina · 75 pts","2" to "You · 55 pts","3" to "Kai · 40 pts")){(rank,name)->Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Text(rank,style=MaterialTheme.typography.titleLarge,modifier=Modifier.width(44.dp));Text(name,style=MaterialTheme.typography.titleMedium)}}
    }
}}
@Composable private fun QuestProgressRow(title:String,detail:String,progress:Float,icon:ImageVector){Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(48.dp),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.secondaryContainer){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(icon,null,tint=MaterialTheme.colorScheme.onSecondaryContainer)}};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(detail,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(8.dp));LinearProgressIndicator({progress},Modifier.fillMaxWidth())}}}}

@Composable private fun OrganizerDashboard(navigate:(VentScreen)->Unit,expanded:Boolean){Page("Event overview",actions={FilledTonalButton(onClick={}){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(6.dp));Text("New session")}}){
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
        item{Text("Vent Demo Day",style=MaterialTheme.typography.titleLarge);Text("Live operations · Thursday, 20 November",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{if(expanded)Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){Metric("38","Assigned",Modifier.weight(1f));Metric("31","Checked in",Modifier.weight(1f));Metric("7","Not arrived",Modifier.weight(1f))}else Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Metric("31 / 40","Checked in");LinearProgressIndicator({31f/40f},Modifier.fillMaxWidth())}}
        item{SectionTitle("Live session","Opening keynote")}
        item{ListItem(headlineContent={Text("Main theatre")},supportingContent={Text("Check-in open · poll ready")},leadingContent={Icon(Icons.Outlined.MeetingRoom,null)},trailingContent={IconButton(onClick={navigate(VentScreen.SEAT)}){Icon(Icons.Outlined.ChevronRight,"Open seating")}})}
        item{SectionTitle("Attention","2 items")}
        item{ListItem(headlineContent={Text("7 attendees not checked in")},supportingContent={Text("Doors close in 18 minutes")},leadingContent={Icon(Icons.Outlined.Schedule,null)})}
    }
}}
@Composable private fun Metric(value:String,label:String,modifier:Modifier=Modifier){Surface(modifier,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.secondaryContainer){Column(Modifier.padding(20.dp)){Text(value,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSecondaryContainer);Text(label,color=MaterialTheme.colorScheme.onSecondaryContainer)}}}

@Composable private fun ProfileScreen(uiState:VentUiState,email:String,onSignOut:()->Unit){Page("Profile"){
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(64.dp),shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("YO",style=MaterialTheme.typography.titleLarge)}};Spacer(Modifier.width(16.dp));Column{Text("Your profile",style=MaterialTheme.typography.titleLarge);Text("55 points · 1 badge",color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
        item{HorizontalDivider()}
        item{ListItem(headlineContent={Text(email)},supportingContent={Text("Registered email")},leadingContent={Icon(Icons.Outlined.Email,null)})}
        item{AssistChip(onClick={},label={Text(if(uiState.role==DemoRole.ATTENDEE)"Attendee" else "Organizer")},leadingIcon={Icon(Icons.Outlined.Badge,null)})}
        item{ListItem(headlineContent={Text("Appearance")},supportingContent={Text("Follows system")},leadingContent={Icon(Icons.Outlined.DarkMode,null)})}
        item{OutlinedButton(onClick=onSignOut,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Sign out")}}
    }
}}

@Composable private fun StatusBanner(message:String,onDismiss:()->Unit){Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.tertiaryContainer){Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.CheckCircle,null,tint=MaterialTheme.colorScheme.onTertiaryContainer);Spacer(Modifier.width(12.dp));Text(message,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onTertiaryContainer,modifier=Modifier.weight(1f));IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"Dismiss message",tint=MaterialTheme.colorScheme.onTertiaryContainer)}}}}
@Composable private fun SectionTitle(title:String,trailing:String){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text(trailing,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}}
@Composable private fun VentBottomBar(items:List<NavItem>,selected:VentScreen,onSelect:(VentScreen)->Unit){NavigationBar(containerColor=MaterialTheme.colorScheme.surfaceContainer){items.forEach{item->NavigationBarItem(selected=selected==item.screen,onClick={onSelect(item.screen)},icon={Icon(item.icon,item.label)},label={Text(item.label,maxLines=1,overflow=TextOverflow.Ellipsis)},colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer))}}}
@Composable private fun VentRail(items:List<NavItem>,selected:VentScreen,onSelect:(VentScreen)->Unit,role:DemoRole){NavigationRail(header={Column(horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.height(18.dp));Surface(Modifier.size(44.dp),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primary){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("V",color=MaterialTheme.colorScheme.onPrimary,fontWeight=FontWeight.Bold,fontSize=22.sp)}};Spacer(Modifier.height(22.dp))}}){items.forEach{item->NavigationRailItem(selected=selected==item.screen,onClick={onSelect(item.screen)},icon={Icon(item.icon,item.label)},label={Text(item.label)})};Spacer(Modifier.weight(1f));Text(if(role==DemoRole.ORGANIZER)"Organizer" else "Attendee",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(18.dp))}}

