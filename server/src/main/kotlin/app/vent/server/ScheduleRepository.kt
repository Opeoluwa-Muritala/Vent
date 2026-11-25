package app.vent.server

import app.vent.api.CreateEventRequest
import app.vent.api.CreateSessionRequest
import app.vent.api.EventDto
import app.vent.api.SessionDto
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

internal class ScheduleRepository(
    private val dataSource: DataSource,
) {
    fun createEvent(
        userId: String,
        request: CreateEventRequest,
    ): EventDto {
        val title = request.title.trim()
        val start = parseTime(request.startsAt)
        val end = parseTime(request.endsAt)
        val seed = parseSeed(request.seedArgb)
        require(title.length in 1..160 && end.isAfter(start)) { "invalid_event" }
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """WITH created AS (
                    INSERT INTO events(id, title, starts_at, ends_at, seed_argb, cover_image_url)
                    SELECT ?, ?, ?, ?, ?, ? WHERE EXISTS (
                        SELECT 1 FROM user_roles WHERE user_id = ?::uuid AND role = 'ORGANIZER'
                    ) RETURNING id
                )
                INSERT INTO user_roles(user_id, event_id, role)
                SELECT ?::uuid, id, 'ORGANIZER' FROM created""",
                ).use { statement ->
                    statement.setObject(1, id)
                    statement.setString(2, title)
                    statement.setObject(3, start)
                    statement.setObject(4, end)
                    statement.setLong(5, seed)
                    statement.setString(6, request.coverImageUrl?.takeIf { it.length <= 2048 && it.startsWith("https://") })
                    statement.setString(7, userId)
                    statement.setString(8, userId)
                    if (statement.executeUpdate() != 1) throw Forbidden()
                }
        }
        return EventDto(id.toString(), title, start.toInstant().toString(), end.toInstant().toString(), request.seedArgb.uppercase())
    }

    fun updateSeed(
        userId: String,
        eventId: String,
        value: String,
    ) {
        val seed = parseSeed(value)
        val event = parseId(eventId)
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """UPDATE events SET seed_argb = ? WHERE id = ? AND EXISTS (
                    SELECT 1 FROM user_roles WHERE event_id = events.id AND user_id = ?::uuid AND role = 'ORGANIZER'
                )""",
                ).use { statement ->
                    statement.setLong(1, seed)
                    statement.setObject(2, event)
                    statement.setString(3, userId)
                    if (statement.executeUpdate() != 1) throw Forbidden()
                }
        }
    }

    fun events(userId: String): List<EventDto> =
        dataSource.connection.use { connection ->
            val events =
                connection
                    .prepareStatement(
                        """SELECT e.id, e.title, e.starts_at, e.ends_at, e.seed_argb
               FROM events e JOIN user_roles r ON r.event_id = e.id
               WHERE r.user_id = ?::uuid AND (e.published OR r.role = 'ORGANIZER')
               ORDER BY e.starts_at, e.id""",
                    ).use { statement ->
                        statement.setString(1, userId)
                        statement.executeQuery().use { rows ->
                            buildList {
                                while (rows.next()) {
                                    add(
                                        EventDto(
                                            rows.getString(1),
                                            rows.getString(2),
                                            rows.getObject(3, OffsetDateTime::class.java).toInstant().toString(),
                                            rows.getObject(4, OffsetDateTime::class.java).toInstant().toString(),
                                            rows
                                                .getLong(5)
                                                .toString(16)
                                                .uppercase()
                                                .padStart(8, '0'),
                                        ),
                                    )
                                }
                            }
                        }
                    }
            events.map { event ->
                val sessions =
                    connection
                        .prepareStatement(
                            """SELECT id, hall_id, title, starts_at, ends_at, checkin_opens_at, checkin_closes_at
                   FROM sessions WHERE event_id = ?::uuid ORDER BY starts_at, id""",
                        ).use { statement ->
                            statement.setString(1, event.id)
                            statement.executeQuery().use { rows ->
                                buildList {
                                    while (rows.next()) {
                                        fun time(column: Int) = rows.getObject(column, OffsetDateTime::class.java).toInstant().toString()
                                        add(
                                            SessionDto(
                                                rows.getString(1),
                                                event.id,
                                                rows.getString(2),
                                                rows.getString(3),
                                                time(4),
                                                time(5),
                                                time(6),
                                                time(7),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                event.copy(sessions = sessions)
            }
        }

    fun createSession(
        userId: String,
        request: CreateSessionRequest,
    ): SessionDto {
        val event = parseId(request.eventId)
        val hall = parseId(request.hallId)
        val title = request.title.trim()
        require(title.length in 1..160) { "invalid_title" }
        val start = parseTime(request.startsAt)
        val end = parseTime(request.endsAt)
        val opens = parseTime(request.checkInOpensAt)
        val closes = parseTime(request.checkInClosesAt)
        require(end.isAfter(start) && closes.isAfter(opens) && !opens.isAfter(end) && !closes.isAfter(end)) { "invalid_session_window" }
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val created =
                    connection
                        .prepareStatement(
                            """INSERT INTO sessions(id, event_id, hall_id, title, starts_at, ends_at, checkin_opens_at, checkin_closes_at)
                       SELECT ?, h.event_id, h.id, ?, ?, ?, ?, ?
                       FROM halls h JOIN user_roles r ON r.event_id = h.event_id
                       WHERE h.id = ? AND h.event_id = ? AND r.user_id = ?::uuid AND r.role = 'ORGANIZER'""",
                        ).use { statement ->
                            statement.setObject(1, id)
                            statement.setString(2, title)
                            listOf(start, end, opens, closes).forEachIndexed { index, time -> statement.setObject(index + 3, time) }
                            statement.setObject(7, hall)
                            statement.setObject(8, event)
                            statement.setString(9, userId)
                            statement.executeUpdate()
                        }
                if (created != 1) throw Forbidden()
                connection.prepareStatement("INSERT INTO session_secrets(session_id, hmac_key) VALUES (?, ?)").use { statement ->
                    statement.setObject(1, id)
                    statement.setBytes(2, ByteArray(32).also(SecureRandom()::nextBytes))
                    statement.executeUpdate()
                }
                connection.commit()
            } catch (failure: Exception) {
                connection.rollback()
                throw failure
            }
        }
        return SessionDto(
            id.toString(),
            event.toString(),
            hall.toString(),
            title,
            start.toInstant().toString(),
            end.toInstant().toString(),
            opens.toInstant().toString(),
            closes.toInstant().toString(),
        )
    }

    private fun parseId(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrElse { throw IllegalArgumentException("invalid_resource_id") }

    private fun parseTime(value: String): OffsetDateTime =
        runCatching { OffsetDateTime.parse(value) }.getOrElse { throw IllegalArgumentException("invalid_timestamp") }

    private fun parseSeed(value: String): Long {
        require(value.matches(Regex("[0-9A-Fa-f]{8}"))) { "invalid_seed" }
        return value.toLong(16)
    }
}
