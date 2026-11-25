package app.vent.server

import app.vent.api.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

internal class EventPassTokens(
    private val algorithm: Algorithm,
) {
    fun issue(
        userId: String,
        eventId: String,
        expires: Instant,
        now: Instant = Instant.now(),
    ): String =
        JWT
            .create()
            .withIssuer("events-pass")
            .withAudience("events-staff")
            .withSubject(userId)
            .withClaim("event", eventId)
            .withClaim("purpose", "admission")
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(now)
            .withExpiresAt(expires)
            .sign(algorithm)

    fun verify(
        token: String,
        eventId: String,
    ): String? {
        if (token.length !in 1..4096) return null
        return runCatching {
            val decoded =
                JWT
                    .require(algorithm)
                    .withIssuer("events-pass")
                    .withAudience("events-staff")
                    .withClaim("purpose", "admission")
                    .withClaim("event", eventId)
                    .build()
                    .verify(token)
            UUID.fromString(decoded.subject).toString()
        }.getOrNull()
    }
}

internal fun Route.passRoutes(
    dataSource: DataSource,
    tokenService: TokenService,
) {
    val access = AccessControl(dataSource)
    val tokens = EventPassTokens(tokenService.algorithm)

    fun details(
        userId: String,
        eventId: String,
    ): Triple<String, String, Instant> =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT u.name, e.title, e.ends_at FROM users u CROSS JOIN events e WHERE u.id=?::uuid AND e.id=?::uuid",
                ).use { statement ->
                    statement.setString(1, userId)
                    statement.setString(2, eventId)
                    statement.executeQuery().use { rows ->
                        require(rows.next()) { "invalid_pass" }
                        Triple(rows.getString(1), rows.getString(2), rows.getTimestamp(3).toInstant())
                    }
                }
        }
    get("/events/{id}/pass") {
        val eventId = UUID.fromString(call.parameters["id"]).toString()
        val userId = requireNotNull(call.principal<UserPrincipal>()).userId
        val pass =
            withContext(Dispatchers.IO) {
                access.require(eventId, userId, "ATTENDEE", "STAFF", "ORGANIZER")
                val (name, title, end) = details(userId, eventId)
                val now = Instant.now()
                require(end.plusSeconds(86_400).isAfter(now)) { "event_ended" }
                val expires = minOf(now.plusSeconds(900), end.plusSeconds(86_400))
                AttendeePassDto(tokens.issue(userId, eventId, expires, now), expires.toString(), name, title)
            }
        call.respond(pass)
    }
    post("/events/{id}/passes/verify") {
        val eventId = UUID.fromString(call.parameters["id"]).toString()
        val staff = requireNotNull(call.principal<UserPrincipal>()).userId
        val request = call.receive<VerifyPassRequest>()
        require(request.token.length in 1..4096) { "invalid_pass" }
        val result =
            withContext(Dispatchers.IO) {
                access.require(eventId, staff, "STAFF", "ORGANIZER")
                val attendee = requireNotNull(tokens.verify(request.token, eventId)) { "invalid_pass" }
                access.require(eventId, attendee, "ATTENDEE", "STAFF", "ORGANIZER")
                val (name, title, _) = details(attendee, eventId)
                VerifiedPassDto(name, title)
            }
        call.respond(result)
    }
}
