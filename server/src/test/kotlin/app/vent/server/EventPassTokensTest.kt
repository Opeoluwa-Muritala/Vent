package app.vent.server

import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventPassTokensTest {
    private val tokens = EventPassTokens(Algorithm.HMAC256("test-only-pass-signing-key"))
    private val user = "10000000-0000-0000-0000-000000000001"
    private val event = "20000000-0000-0000-0000-000000000001"

    @Test fun issuedPassIsBoundToTheAttendeeAndEvent() {
        val token = tokens.issue(user, event, Instant.now().plusSeconds(900))
        assertEquals(user, tokens.verify(token, event))
        assertNull(tokens.verify(token, "30000000-0000-0000-0000-000000000001"))
    }

    @Test fun expiredTamperedAndAccessTokensAreRejected() {
        assertNull(tokens.verify(tokens.issue(user, event, Instant.EPOCH.plusSeconds(60), Instant.EPOCH), event))
        val token = tokens.issue(user, event, Instant.now().plusSeconds(900))
        assertNull(tokens.verify(token + "x", event))
        assertNull(tokens.verify(TokenService("test-only-pass-signing-key").accessToken(user).first, event))
    }
}
