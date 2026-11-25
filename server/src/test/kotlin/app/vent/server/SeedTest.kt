package app.vent.server

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeedTest {
    @Test
    fun seedCreatesOneDemoScheduleAcrossRepeatedRuns() {
        EmbeddedPostgres.start().use { postgres ->
            val source = postgres.postgresDatabase
            Flyway
                .configure()
                .dataSource(source)
                .load()
                .migrate()
            source.connection.use { connection ->
                val now = Instant.parse("2026-11-19T12:00:00Z")
                seedDemo(connection, now)
                seedDemo(connection, now.plusSeconds(3600))
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT starts_at FROM events WHERE title = 'Vent Demo Day'").use {
                        assertTrue(it.next())
                        assertEquals(now.plusSeconds(7 * 86400L), it.getObject(1, OffsetDateTime::class.java).toInstant())
                        assertEquals(false, it.next())
                    }
                    statement.executeQuery("SELECT count(*), count(DISTINCT hmac_key) FROM session_secrets").use {
                        assertTrue(it.next())
                        assertEquals(2, it.getInt(1))
                        assertEquals(2, it.getInt(2))
                    }
                }
            }
        }
    }
}
