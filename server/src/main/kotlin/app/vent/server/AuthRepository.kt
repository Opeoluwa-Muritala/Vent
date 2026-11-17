package app.vent.server

import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

data class AuthUser(val id: String, val name: String, val email: String, val passwordHash: String)

class AuthRepository(private val dataSource: DataSource) {
    fun createUser(name: String, email: String, passwordHash: String): AuthUser {
        val user = AuthUser(UUID.randomUUID().toString(), name.trim(), email.trim().lowercase(), passwordHash)
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO users(id,name,email,password_hash) VALUES (?::uuid,?,?,?)").use { statement ->
                statement.setString(1, user.id); statement.setString(2, user.name); statement.setString(3, user.email); statement.setString(4, user.passwordHash)
                try { statement.executeUpdate() } catch (error: SQLException) { if (error.sqlState == "23505") throw EmailAlreadyExists() else throw error }
            }
        }
        return user
    }

    fun findByEmail(email: String): AuthUser? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT id,name,email,password_hash FROM users WHERE lower(email)=?").use { statement ->
            statement.setString(1, email.trim().lowercase())
            statement.executeQuery().use { rows -> if (rows.next()) AuthUser(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4)) else null }
        }
    }

    fun findById(id: String): AuthUser? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT id,name,email,password_hash FROM users WHERE id=?::uuid").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows -> if (rows.next()) AuthUser(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4)) else null }
        }
    }

    fun storeRefreshToken(userId: String, hash: String, expiresAt: Instant = Instant.now().plus(30, ChronoUnit.DAYS)): String {
        val id = UUID.randomUUID().toString()
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO refresh_tokens(id,user_id,token_hash,expires_at) VALUES (?::uuid,?::uuid,?,?)").use { statement ->
                statement.setString(1, id); statement.setString(2, userId); statement.setString(3, hash); statement.setObject(4, expiresAt); statement.executeUpdate()
            }
        }
        return id
    }

    fun rotateRefreshToken(oldHash: String, newHash: String): String? = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            val old = connection.prepareStatement("SELECT id,user_id FROM refresh_tokens WHERE token_hash=? AND revoked_at IS NULL AND expires_at>now() FOR UPDATE").use { statement ->
                statement.setString(1, oldHash); statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) to rows.getString(2) else null }
            } ?: return@use null
            val replacementId = UUID.randomUUID().toString()
            connection.prepareStatement("INSERT INTO refresh_tokens(id,user_id,token_hash,expires_at) VALUES (?::uuid,?::uuid,?,now()+interval '30 days')").use { statement ->
                statement.setString(1, replacementId); statement.setString(2, old.second); statement.setString(3, newHash); statement.executeUpdate()
            }
            connection.prepareStatement("UPDATE refresh_tokens SET revoked_at=now(),replaced_by=?::uuid WHERE id=?::uuid").use { statement ->
                statement.setString(1, replacementId); statement.setString(2, old.first); statement.executeUpdate()
            }
            connection.commit(); old.second
        } catch (error: Throwable) { connection.rollback(); throw error } finally { connection.autoCommit = true }
    }
}

class EmailAlreadyExists : RuntimeException()