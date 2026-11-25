package app.vent.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.auth.Principal
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val ISSUER = "vent-api"
private const val AUDIENCE = "vent-clients"

data class UserPrincipal(
    val userId: String,
) : Principal

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

class TokenService(
    secret: String,
) {
    val algorithm: Algorithm = Algorithm.HMAC256(secret)
    private val secretBytes = secret.toByteArray(Charsets.UTF_8)
    private val random = SecureRandom()

    fun accessToken(
        userId: String,
        now: Instant = Instant.now(),
    ): Pair<String, Long> {
        val ttl = 60 * 60L
        return JWT
            .create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(userId)
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(now)
            .withExpiresAt(now.plusSeconds(ttl))
            .sign(algorithm) to ttl
    }

    fun refreshToken(): String = ByteArray(48).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    fun hashRefreshToken(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    fun hashOtp(
        email: String,
        code: String,
    ): String = keyedHash("otp:$email:$code")

    fun hashRemoteAddress(address: String): String = keyedHash("remote:$address")

    fun verifier() =
        JWT
            .require(algorithm)
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .build()

    private fun keyedHash(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
