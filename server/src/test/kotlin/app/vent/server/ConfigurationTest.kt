package app.vent.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ConfigurationTest {
    @Test fun rejectsShortJwtSecrets() {
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "short")) }
    }

    @Test fun parsesRailwayPostgresUrlWithoutLoggingCredentials() {
        val config =
            AppConfig.fromEnvironment(
                mapOf(
                    "JWT_SECRET" to "01234567890123456789012345678901",
                    "DATABASE_URL" to "postgresql://vent:secret@db.example:5432/vent",
                ),
            )
        assertEquals("jdbc:postgresql://db.example:5432/vent", config.databaseUrl)
        assertEquals("vent", config.databaseUser)
        assertEquals("secret", config.databasePassword)
    }

    @Test fun accessTokensHaveIssuerAudienceAndExpiry() {
        val service = TokenService("01234567890123456789012345678901")
        val token = service.accessToken("user-id").first
        val decoded = service.verifier().verify(token)
        assertEquals("user-id", decoded.subject)
        assertTrue(decoded.expiresAtAsInstant.isAfter(decoded.issuedAtAsInstant))
        assertEquals(3600, decoded.expiresAtAsInstant.epochSecond - decoded.issuedAtAsInstant.epochSecond)
    }

    @Test fun otpHashesAreKeyedAndBoundToEmail() {
        val first = TokenService("01234567890123456789012345678901")
        val second = TokenService("abcdefghijklmnopqrstuvwxyz123456")
        val hash = first.hashOtp("ada@company.com", "123456")
        assertEquals(hash, first.hashOtp("ada@company.com", "123456"))
        assertNotEquals(hash, first.hashOtp("other@company.com", "123456"))
        assertNotEquals(hash, second.hashOtp("ada@company.com", "123456"))
    }
}
