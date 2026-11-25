package app.vent.server

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.sql.DataSource

class CheckInTokenService(
    private val dataSource: DataSource,
) {
    enum class Type { SCREEN, PRINTED }

    data class Claims(
        val type: Type,
        val sessionId: String,
        val value: Long,
    )

    fun issueScreen(
        sessionId: String,
        now: Instant = Instant.now(),
    ): String = issue(Type.SCREEN, sessionId, now.epochSecond / 30)

    fun issuePrinted(sessionId: String): String = issue(Type.PRINTED, sessionId, sessionSecret(sessionId).second.toLong())

    fun verify(
        token: String,
        expectedSessionId: String,
        now: Instant = Instant.now(),
    ): Claims? {
        if (token.length !in 32..2048) return null
        val parts = token.split('.')
        if (parts.size != 2) return null
        val payload = runCatching { String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val fields = payload.split('|')
        if (fields.size != 3) return null
        val type = runCatching { Type.valueOf(fields[0]) }.getOrNull() ?: return null
        val sessionId = fields[1]
        val value = fields[2].toLongOrNull() ?: return null
        if (sessionId != expectedSessionId) return null
        val (key, printedEpoch) = sessionSecret(sessionId)
        val expected = hmac(key, parts[0])
        val supplied = runCatching { Base64.getUrlDecoder().decode(parts[1]) }.getOrNull() ?: return null
        if (!MessageDigest.isEqual(expected, supplied)) return null
        val validValue =
            when (type) {
                Type.SCREEN -> value == now.epochSecond / 30 || value == now.epochSecond / 30 - 1
                Type.PRINTED ->
                    value ==
                        printedEpoch.toLong()
            }
        if (!validValue || !insideCheckInWindow(sessionId, now)) return null
        return Claims(type, sessionId, value)
    }

    private fun issue(
        type: Type,
        sessionId: String,
        value: Long,
    ): String {
        val payload =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                "${type.name}|$sessionId|$value".toByteArray(StandardCharsets.UTF_8),
            )
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(sessionSecret(sessionId).first, payload))
        return "$payload.$signature"
    }

    private fun hmac(
        key: ByteArray,
        payload: String,
    ): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(payload.toByteArray(StandardCharsets.US_ASCII))
        }

    private fun sessionSecret(sessionId: String): Pair<ByteArray, Int> =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT hmac_key,printed_epoch FROM session_secrets WHERE session_id=?::uuid").use { statement ->
                statement.setString(1, sessionId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) throw IllegalStateException("Session secret is unavailable")
                    rows.getBytes(1) to
                        rows.getInt(2)
                }
            }
        }

    private fun insideCheckInWindow(
        sessionId: String,
        now: Instant,
    ): Boolean =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT ? BETWEEN checkin_opens_at AND checkin_closes_at FROM sessions WHERE id=?::uuid",
                ).use { statement ->
                    statement.setObject(1, now)
                    statement.setString(2, sessionId)
                    statement.executeQuery().use { rows ->
                        rows.next() &&
                            rows.getBoolean(1)
                    }
                }
        }
}
