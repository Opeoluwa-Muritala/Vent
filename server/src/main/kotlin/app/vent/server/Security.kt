package app.vent.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import de.mkammerer.argon2.Argon2Factory
import io.ktor.server.auth.Principal
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

private const val ISSUER = "vent-api"
private const val AUDIENCE = "vent-clients"

data class UserPrincipal(val userId: String) : Principal

data class TokenPair(val accessToken: String, val refreshToken: String, val expiresInSeconds: Long)

class PasswordHasher {
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    fun hash(password: CharArray): String = try { argon2.hash(3, 65_536, 1, password) } finally { password.fill('\u0000') }
    fun verify(hash: String, password: CharArray): Boolean = try { argon2.verify(hash, password) } finally { password.fill('\u0000') }
}

class TokenService(secret: String) {
    val algorithm: Algorithm = Algorithm.HMAC256(secret)
    private val random = SecureRandom()
    fun accessToken(userId: String, now: Instant = Instant.now()): Pair<String, Long> {
        val ttl = 15 * 60L
        return JWT.create().withIssuer(ISSUER).withAudience(AUDIENCE).withSubject(userId).withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(now).withExpiresAt(now.plusSeconds(ttl)).sign(algorithm) to ttl
    }
    fun refreshToken(): String = ByteArray(48).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    fun hashRefreshToken(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    fun verifier() = JWT.require(algorithm).withIssuer(ISSUER).withAudience(AUDIENCE).build()
}