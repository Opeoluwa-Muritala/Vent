package app.vent.server

import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

data class AuthUser(
    val id: String,
    val name: String,
    val email: String,
)

sealed interface OtpConsumeResult {
    data class Success(
        val userId: String,
    ) : OtpConsumeResult

    data class Wrong(
        val triesLeft: Int,
    ) : OtpConsumeResult

    data object Expired : OtpConsumeResult

    data object Locked : OtpConsumeResult
}

class OtpRateLimited : RuntimeException()

class AuthRepository(
    private val dataSource: DataSource,
) {
    fun findByEmail(email: String): AuthUser? =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT id,name,email FROM users WHERE lower(email)=?").use { statement ->
                statement.setString(1, email.trim().lowercase())
                statement.executeQuery().use { rows ->
                    if (rows.next()) AuthUser(rows.getString(1), rows.getString(2), rows.getString(3)) else null
                }
            }
        }

    fun findById(id: String): AuthUser? =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT id,name,email FROM users WHERE id=?::uuid").use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { rows ->
                    if (rows.next()) AuthUser(rows.getString(1), rows.getString(2), rows.getString(3)) else null
                }
            }
        }

    fun storeOtp(
        email: String,
        codeHash: String,
        ipHash: String,
        now: Instant,
    ): Boolean =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val since = now.minus(1, ChronoUnit.MINUTES)
                if (countSince(connection, "lower(email)=?", email, since) >= 3 ||
                    countSince(connection, "requested_ip_hash=?", ipHash, since) >= 20
                ) {
                    throw OtpRateLimited()
                }
                connection.prepareStatement("UPDATE otp_codes SET consumed_at=? WHERE lower(email)=? AND consumed_at IS NULL").use {
                    it.setObject(1, now)
                    it.setString(2, email)
                    it.executeUpdate()
                }
                connection
                    .prepareStatement(
                        "INSERT INTO otp_codes(id,email,code_hash,requested_ip_hash,expires_at) VALUES (?::uuid,?,?,?,?)",
                    ).use {
                        it.setString(1, UUID.randomUUID().toString())
                        it.setString(2, email)
                        it.setString(3, codeHash)
                        it.setString(4, ipHash)
                        it.setObject(5, now.plus(10, ChronoUnit.MINUTES))
                        it.executeUpdate()
                    }
                val registered =
                    connection.prepareStatement("SELECT 1 FROM users WHERE lower(email)=?").use {
                        it.setString(1, email)
                        it.executeQuery().use { rows -> rows.next() }
                    }
                connection.commit()
                registered
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }

    fun consumeOtp(
        email: String,
        candidateHash: String,
        now: Instant,
    ): OtpConsumeResult =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val record =
                    connection
                        .prepareStatement(
                            "SELECT id,code_hash,expires_at,attempts FROM otp_codes WHERE lower(email)=? AND consumed_at IS NULL ORDER BY created_at DESC LIMIT 1 FOR UPDATE",
                        ).use {
                            it.setString(1, email)
                            it.executeQuery().use { rows ->
                                if (rows.next()) {
                                    OtpRecord(
                                        rows.getString(1),
                                        rows.getString(2),
                                        rows.getObject(3, Instant::class.java),
                                        rows.getInt(4),
                                    )
                                } else {
                                    null
                                }
                            }
                        }
                val result =
                    when {
                        record == null -> OtpConsumeResult.Expired
                        record.attempts >= 5 -> OtpConsumeResult.Locked
                        !record.expiresAt.isAfter(now) -> {
                            consume(connection, record.id, now)
                            OtpConsumeResult.Expired
                        }
                        !MessageDigest.isEqual(record.codeHash.toByteArray(), candidateHash.toByteArray()) -> {
                            val attempts = record.attempts + 1
                            connection
                                .prepareStatement(
                                    "UPDATE otp_codes SET attempts=?, consumed_at=CASE WHEN ?>=5 THEN ? ELSE NULL END WHERE id=?::uuid",
                                ).use {
                                    it.setInt(1, attempts)
                                    it.setInt(2, attempts)
                                    it.setObject(3, now)
                                    it.setString(4, record.id)
                                    it.executeUpdate()
                                }
                            if (attempts >= 5) OtpConsumeResult.Locked else OtpConsumeResult.Wrong(5 - attempts)
                        }
                        else -> {
                            consume(connection, record.id, now)
                            val userId =
                                connection.prepareStatement("SELECT id FROM users WHERE lower(email)=?").use {
                                    it.setString(1, email)
                                    it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
                                }
                            if (userId == null) OtpConsumeResult.Expired else OtpConsumeResult.Success(userId)
                        }
                    }
                connection.commit()
                result
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }

    fun storeRefreshToken(
        userId: String,
        hash: String,
        expiresAt: Instant = Instant.now().plus(30, ChronoUnit.DAYS),
    ): String {
        val id = UUID.randomUUID().toString()
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO refresh_tokens(id,user_id,token_hash,expires_at) VALUES (?::uuid,?::uuid,?,?)").use {
                it.setString(1, id)
                it.setString(2, userId)
                it.setString(3, hash)
                it.setObject(4, expiresAt)
                it.executeUpdate()
            }
        }
        return id
    }

    fun rotateRefreshToken(
        oldHash: String,
        newHash: String,
    ): String? =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val old =
                    connection
                        .prepareStatement(
                            "SELECT id,user_id FROM refresh_tokens WHERE token_hash=? AND revoked_at IS NULL AND expires_at>now() FOR UPDATE",
                        ).use {
                            it.setString(1, oldHash)
                            it.executeQuery().use { rows ->
                                if (rows.next()) rows.getString(1) to rows.getString(2) else null
                            }
                        } ?: return@use null
                val replacementId = UUID.randomUUID().toString()
                connection
                    .prepareStatement(
                        "INSERT INTO refresh_tokens(id,user_id,token_hash,expires_at) VALUES (?::uuid,?::uuid,?,now()+interval '30 days')",
                    ).use {
                        it.setString(1, replacementId)
                        it.setString(2, old.second)
                        it.setString(3, newHash)
                        it.executeUpdate()
                    }
                connection.prepareStatement("UPDATE refresh_tokens SET revoked_at=now(),replaced_by=?::uuid WHERE id=?::uuid").use {
                    it.setString(1, replacementId)
                    it.setString(2, old.first)
                    it.executeUpdate()
                }
                connection.commit()
                old.second
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }

    private fun countSince(
        connection: java.sql.Connection,
        predicate: String,
        value: String,
        since: Instant,
    ): Int =
        connection.prepareStatement("SELECT count(*) FROM otp_codes WHERE $predicate AND created_at>=?").use {
            it.setString(1, value)
            it.setObject(2, since)
            it.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private fun consume(
        connection: java.sql.Connection,
        id: String,
        now: Instant,
    ) {
        connection.prepareStatement("UPDATE otp_codes SET consumed_at=? WHERE id=?::uuid").use {
            it.setObject(1, now)
            it.setString(2, id)
            it.executeUpdate()
        }
    }

    private data class OtpRecord(
        val id: String,
        val codeHash: String,
        val expiresAt: Instant,
        val attempts: Int,
    )
}
