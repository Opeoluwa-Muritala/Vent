package app.vent.server

import app.vent.api.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

class AccessControl(private val dataSource: DataSource) {
    fun requireAnyOrganizer(userId: String) {
        val found = dataSource.connection.use { connection -> connection.prepareStatement("SELECT 1 FROM user_roles WHERE user_id=?::uuid AND role='ORGANIZER' LIMIT 1").use { statement -> statement.setString(1,userId); statement.executeQuery().use { it.next() } } }
        if (!found) throw Forbidden()
    }
    fun require(eventId: String, userId: String, vararg allowed: String) {
        val role = dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT role FROM user_roles WHERE event_id=?::uuid AND user_id=?::uuid").use { statement ->
                statement.setString(1, eventId); statement.setString(2, userId)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
        }
        if (role !in allowed) throw Forbidden()
    }
}
class Forbidden : RuntimeException()

private object DemoState {
    const val eventId = "10000000-0000-0000-0000-000000000001"
    const val hallId = "20000000-0000-0000-0000-000000000001"
    const val sessionId = "30000000-0000-0000-0000-000000000001"
    val version = AtomicLong(1)
    val pollAnswers = ConcurrentHashMap<String, Int>()
    val event = EventDto(eventId, "Vent Demo Day", "2026-11-20T09:00:00Z", "2026-11-20T17:00:00Z", "FF6750A4", listOf(SessionDto(sessionId,eventId,hallId,"Opening Keynote","2026-11-20T09:30:00Z","2026-11-20T10:30:00Z","2026-11-20T09:00:00Z","2026-11-20T10:00:00Z")))
    val seats = buildList {
        for (row in 0 until 5) for (column in 0 until 8) {
            val id = "${row + 1}-${column + 1}"
            add(SeatDto(id,"${('A'.code + row).toChar()}${column + 1}",column * 1.15 + if(column >= 4) 1.2 else 0.0,row * 1.1, (column - 3.5) * 1.5, if(id=="1-1") "ACCESSIBLE" else if(id=="3-8") "BLOCKED" else "STANDARD", if(id=="3-8") "BLOCKED" else "FREE", id=="2-4"))
        }
    }
    val hall = HallDto(hallId,"Main theatre",12.0,8.0,seats,listOf(HallElementDto("STAGE","Stage",1.0,0.2,10.0,0.8),HallElementDto("EXIT","Exit",0.1,6.5,0.5,1.0)))
}

internal fun Route.ventDemoRoutes(access: AccessControl, checkInTokens: CheckInTokenService, schedule: ScheduleRepository) {
    fun userId(call: io.ktor.server.application.ApplicationCall) = requireNotNull(call.principal<UserPrincipal>()).userId
    get("/events") { call.respond(schedule.events(userId(call))) }
    get("/events/{id}") {
        val event = schedule.events(userId(call)).firstOrNull { it.id == call.parameters["id"] }
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("not_found", "Event not found"))
        call.respond(event)
    }
    get("/halls/{id}") {
        if (call.parameters["id"] != DemoState.hallId) return@get call.respond(HttpStatusCode.NotFound, ApiError("not_found","Hall not found"))
        call.respond(DemoState.hall)
    }
    get("/sessions/{id}/seats") {
        if (call.parameters["id"] != DemoState.sessionId) return@get call.respond(HttpStatusCode.NotFound, ApiError("not_found","Session not found"))
        val since = call.request.queryParameters["since"]?.toLongOrNull()
        val current = DemoState.version.get()
        call.respond(SeatSnapshotDto(current, since == null || since < current - 20, if (since == current) emptyList() else DemoState.seats))
    }
    post("/sessions/{id}/assignments/preview") {
        access.require(DemoState.eventId,userId(call),"ORGANIZER")
        val request = call.receive<AssignmentPreviewRequest>()
        require(request.attendeeIds.size in 1..500) { "invalid_attendee_count" }
        val assignable = DemoState.seats.filter { it.state == "FREE" && it.type != "BLOCKED" }
        call.respond(AssignmentPreviewDto(UUID.randomUUID().toString(),DemoState.version.get(),request.attendeeIds.zip(assignable.map(SeatDto::id)).toMap(),request.attendeeIds.drop(assignable.size)))
    }
    post("/sessions/{id}/assignments/commit") {
        access.require(DemoState.eventId,userId(call),"ORGANIZER")
        val request = call.receive<AssignmentCommitRequest>()
        if (request.baseVersion != DemoState.version.get()) return@post call.respond(HttpStatusCode.Conflict,ApiError("stale_preview","Seat data changed; preview again"))
        DemoState.version.incrementAndGet(); call.respond(HttpStatusCode.NoContent)
    }
    post("/sessions/{id}/checkin") {
        val request = call.receive<CheckInRequest>()
        require(request.deviceId.length in 8..128) { "invalid_device" }
        if (checkInTokens.verify(request.token, DemoState.sessionId) == null) return@post call.respond(HttpStatusCode.BadRequest, ApiError("invalid_checkin_code","The code is invalid, expired, or for another session"))
        call.respond(CheckInResponse("checked_in","B4",10))
    }
    post("/sessions/{id}/checkin/manual") {
        access.require(DemoState.eventId,userId(call),"STAFF","ORGANIZER")
        val request = call.receive<ManualCheckInRequest>(); require(request.bookingCode.length in 6..128) { "invalid_booking_code" }
        call.respond(CheckInResponse("checked_in","B4",10))
    }
    post("/sessions/{id}/polls") {
        access.require(DemoState.eventId,userId(call),"ORGANIZER")
        val request=call.receive<CreatePollRequest>(); require(request.question.length in 3..300 && request.options.size in 2..8 && request.options.all { it.length in 1..160 }) { "invalid_poll" }
        call.respond(HttpStatusCode.Created,PollDto("40000000-0000-0000-0000-000000000001",DemoState.sessionId,request.question,request.options,"OPEN"))
    }
    post("/polls/{id}/answer") {
        val request=call.receive<PollAnswerRequest>(); require(request.optionIndex in 0..7) { "invalid_option" }
        if (DemoState.pollAnswers.putIfAbsent(userId(call),request.optionIndex) != null) return@post call.respond(HttpStatusCode.Conflict,ApiError("already_answered","You already answered this poll"))
        call.respond(HttpStatusCode.NoContent)
    }
    get("/polls/{id}/results") {
        val counts=(0..3).map { option -> DemoState.pollAnswers.values.count { it==option } }
        call.respond(PollResultsDto(call.parameters["id"].orEmpty(),counts,counts.sum()))
    }
    get("/events/{id}/leaderboard") { call.respond(listOf(LeaderboardEntryDto(1,"Amina",75,false),LeaderboardEntryDto(2,"You",55,true),LeaderboardEntryDto(3,"Kai",40,false))) }
    get("/events/{id}/quests") { call.respond(listOf(QuestDto("q1","SESSIONS_CHECKED_IN",2,1,30,BadgeDto("b1","Full House","Attend both sessions"),false),QuestDto("q2","POLLS_ANSWERED",3,2,15,BadgeDto("b2","Voice","Share your perspective"),false))) }
    post("/events/{id}/wrapped/generate") { access.require(DemoState.eventId,userId(call),"ORGANIZER"); call.respond(HttpStatusCode.Accepted) }
    get("/events/{id}/wrapped/me") { call.respond(WrappedCardDto(2,3,75,listOf(BadgeDto("b1","Full House","Attend both sessions")),2)) }
    get("/events/{id}/occupancy") { access.require(DemoState.eventId,userId(call),"ORGANIZER"); call.respond(listOf(OccupancyDto(DemoState.sessionId,40,38,31,7))) }
    post("/events") {
        call.respond(HttpStatusCode.Created, schedule.createEvent(userId(call), call.receive<CreateEventRequest>()))
    }
    put("/events/{id}/seed") {
        schedule.updateSeed(userId(call), call.parameters["id"].orEmpty(), call.receive<UpdateSeedRequest>().seedArgb)
        call.respond(HttpStatusCode.NoContent)
    }
    post("/halls/{id}/layout") {
        access.require(DemoState.eventId,userId(call),"ORGANIZER");val request=call.receive<SaveHallLayoutRequest>();require(request.width>0&&request.height>0&&request.seats.size<=500){"invalid_layout"};DemoState.version.incrementAndGet();call.respond(HttpStatusCode.NoContent)
    }
    post("/sessions") {
        call.respond(HttpStatusCode.Created, schedule.createSession(userId(call), call.receive<CreateSessionRequest>()))
    }
    post("/sessions/{id}/seats/{seatId}/unassign") { access.require(DemoState.eventId,userId(call),"ORGANIZER");DemoState.version.incrementAndGet();call.respond(HttpStatusCode.NoContent) }
    get("/sessions/{id}/checkin-codes/current") { access.require(DemoState.eventId,userId(call),"ORGANIZER");call.respond(CheckInCodeDto(checkInTokens.issueScreen(DemoState.sessionId),"SCREEN",Instant.now().plusSeconds(30).toString())) }
    post("/sessions/{id}/printed-code/rotate") { access.require(DemoState.eventId,userId(call),"ORGANIZER");call.respond(CheckInCodeDto(checkInTokens.issuePrinted(DemoState.sessionId),"PRINTED",null)) }
    post("/polls/{id}/close") { access.require(DemoState.eventId,userId(call),"ORGANIZER");call.respond(HttpStatusCode.NoContent) }
    post("/events/{id}/quests") {
        access.require(DemoState.eventId,userId(call),"ORGANIZER");val request=call.receive<CreateQuestRequest>();require(request.target in 1..100&&request.points in 0..1000){"invalid_quest"};call.respond(HttpStatusCode.Created,QuestDto(UUID.randomUUID().toString(),request.type,request.target,0,request.points,BadgeDto(request.badgeId,"New badge","Event badge"),false))
    }
}
