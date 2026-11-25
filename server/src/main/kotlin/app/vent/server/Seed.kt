package app.vent.server

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

fun main() {
    val config = AppConfig.fromEnvironment()
    DatabaseFactory.connect(config).use { dataSource ->
        dataSource.connection.use { connection ->
            seedDemo(connection)
        }
    }
}

internal fun seedDemo(
    connection: java.sql.Connection,
    now: Instant = Instant.now(),
) {
    val eventStart = now.plus(7, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC)
    connection.autoCommit = false
    try {
        val eventId = "10000000-0000-0000-0000-000000000001"
        val hallId = "20000000-0000-0000-0000-000000000001"
        val sessionIds = listOf("30000000-0000-0000-0000-000000000001", "30000000-0000-0000-0000-000000000002")
        connection
            .prepareStatement(
                "INSERT INTO events(id,title,starts_at,ends_at,seed_argb,published) VALUES (?::uuid,?,?,?,?,true) ON CONFLICT(id) DO NOTHING",
            ).use {
                it.setString(1, eventId)
                it.setString(2, "Vent Demo Day")
                it.setObject(3, eventStart)
                it.setObject(4, eventStart.plusHours(8))
                it.setLong(5, 0xFF6750A4)
                it.executeUpdate()
            }
        connection
            .prepareStatement(
                "INSERT INTO halls(id,event_id,name,width,height) VALUES (?::uuid,?::uuid,?,?,?) ON CONFLICT(id) DO NOTHING",
            ).use {
                it.setString(1, hallId)
                it.setString(2, eventId)
                it.setString(3, "Main theatre")
                it.setDouble(4, 12.0)
                it.setDouble(5, 8.0)
                it.executeUpdate()
            }
        val users =
            listOf(
                Triple("50000000-0000-0000-0000-000000000001", "organizer@vent.demo", "ORGANIZER"),
                Triple("50000000-0000-0000-0000-000000000002", "staff@vent.demo", "STAFF"),
                Triple("50000000-0000-0000-0000-000000000003", "attendee@vent.demo", "ATTENDEE"),
                Triple("50000000-0000-0000-0000-000000000004", "amina@vent.demo", "ATTENDEE"),
                Triple("50000000-0000-0000-0000-000000000005", "kai@vent.demo", "ATTENDEE"),
            )
        users.forEach { (id, email, role) ->
            connection.prepareStatement("INSERT INTO users(id,name,email) VALUES (?::uuid,?,?) ON CONFLICT(id) DO NOTHING").use {
                it.setString(1, id)
                it.setString(2, email.substringBefore('@').replaceFirstChar(Char::uppercase))
                it.setString(3, email)
                it.executeUpdate()
            }
            connection
                .prepareStatement(
                    "INSERT INTO user_roles(user_id,event_id,role) VALUES (?::uuid,?::uuid,?) ON CONFLICT DO NOTHING",
                ).use {
                    it.setString(1, id)
                    it.setString(2, eventId)
                    it.setString(3, role)
                    it.executeUpdate()
                }
        }
        val seatIds = mutableListOf<String>()
        for (row in 0 until 5) {
            for (column in 0 until 8) {
                val seatId = UUID.nameUUIDFromBytes("vent-seat-$row-$column".toByteArray()).toString()
                seatIds += seatId
                val type =
                    when {
                        row == 0 && column == 0 -> "ACCESSIBLE"
                        row == 2 && column == 7 -> "BLOCKED"
                        else -> "STANDARD"
                    }
                connection
                    .prepareStatement(
                        "INSERT INTO seats(id,hall_id,label,x,y,rotation,type) VALUES (?::uuid,?::uuid,?,?,?,?,?) ON CONFLICT(id) DO NOTHING",
                    ).use {
                        it.setString(1, seatId)
                        it.setString(2, hallId)
                        it.setString(3, "${('A'.code + row).toChar()}${column + 1}")
                        it.setDouble(
                            4,
                            column * 1.15 +
                                if (column >=
                                    4
                                ) {
                                    1.2
                                } else {
                                    0.0
                                },
                        )
                        it.setDouble(5, row * 1.1 + 1.5)
                        it.setDouble(6, (column - 3.5) * 1.5)
                        it.setString(7, type)
                        it.executeUpdate()
                    }
            }
        }
        val random = java.security.SecureRandom()
        sessionIds.forEachIndexed { index, id ->
            val start = eventStart.plusHours(index * 3L)
            val sessionKey = ByteArray(32).also(random::nextBytes)
            connection
                .prepareStatement(
                    "INSERT INTO sessions(id,event_id,hall_id,title,starts_at,ends_at,checkin_opens_at,checkin_closes_at) VALUES (?::uuid,?::uuid,?::uuid,?,?,?,?,?) ON CONFLICT(id) DO NOTHING",
                ).use {
                    it.setString(1, id)
                    it.setString(2, eventId)
                    it.setString(3, hallId)
                    it.setString(
                        4,
                        if (index ==
                            0
                        ) {
                            "Opening keynote"
                        } else {
                            "Build lab"
                        },
                    )
                    it.setObject(5, start)
                    it.setObject(6, start.plus(1, ChronoUnit.HOURS))
                    it.setObject(7, start.minus(30, ChronoUnit.MINUTES))
                    it.setObject(8, start.plus(30, ChronoUnit.MINUTES))
                    it.executeUpdate()
                }
            connection
                .prepareStatement(
                    "INSERT INTO session_secrets(session_id,hmac_key,printed_epoch) VALUES (?::uuid,?,1) ON CONFLICT(session_id) DO NOTHING",
                ).use {
                    it.setString(1, id)
                    it.setBytes(2, sessionKey)
                    it.executeUpdate()
                }
        }
        connection.commit()
        println("Vent demo seed is ready. Request a one-time code with VENT_DEMO_OTP_LOGGING=true for local demos.")
    } catch (error: Throwable) {
        connection.rollback()
        throw error
    } finally {
        connection.autoCommit = true
    }
}
