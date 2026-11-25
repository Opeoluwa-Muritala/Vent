package app.vent.server

import java.security.SecureRandom
import java.time.Instant

interface EmailSender {
    fun sendSignInCode(
        email: String,
        code: String,
    )
}

class DemoEmailSender(
    private val enabled: Boolean,
) : EmailSender {
    override fun sendSignInCode(
        email: String,
        code: String,
    ) {
        if (enabled) println("DEMO ONLY: sign-in code for $email is $code")
    }
}

sealed interface OtpVerification {
    data class Success(
        val tokens: TokenPair,
    ) : OtpVerification

    data class Wrong(
        val triesLeft: Int,
    ) : OtpVerification

    data object Expired : OtpVerification

    data object Locked : OtpVerification
}

class AuthService(
    private val repository: AuthRepository,
    private val tokens: TokenService,
    private val emailSender: EmailSender,
    private val random: SecureRandom = SecureRandom(),
) {
    fun requestCode(
        rawEmail: String,
        remoteAddress: String,
        now: Instant = Instant.now(),
    ) {
        val email = normalizeEmail(rawEmail)
        val code = random.nextInt(1_000_000).toString().padStart(6, '0')
        val registered = repository.storeOtp(email, tokens.hashOtp(email, code), tokens.hashRemoteAddress(remoteAddress), now)
        if (registered) emailSender.sendSignInCode(email, code)
    }

    fun verifyCode(
        rawEmail: String,
        code: String,
        now: Instant = Instant.now(),
    ): OtpVerification {
        val email = normalizeEmail(rawEmail)
        require(code.length == 6 && code.all(Char::isDigit)) { "invalid_code" }
        return when (val result = repository.consumeOtp(email, tokens.hashOtp(email, code), now)) {
            is OtpConsumeResult.Success -> OtpVerification.Success(issue(result.userId))
            is OtpConsumeResult.Wrong -> OtpVerification.Wrong(result.triesLeft)
            OtpConsumeResult.Expired -> OtpVerification.Expired
            OtpConsumeResult.Locked -> OtpVerification.Locked
        }
    }

    fun refresh(refreshToken: String): TokenPair? {
        if (refreshToken.length !in 43..128) return null
        val replacement = tokens.refreshToken()
        val userId =
            repository.rotateRefreshToken(tokens.hashRefreshToken(refreshToken), tokens.hashRefreshToken(replacement)) ?: return null
        val access = tokens.accessToken(userId)
        return TokenPair(access.first, replacement, access.second)
    }

    private fun issue(userId: String): TokenPair {
        val access = tokens.accessToken(userId)
        val refresh = tokens.refreshToken()
        repository.storeRefreshToken(userId, tokens.hashRefreshToken(refresh))
        return TokenPair(access.first, refresh, access.second)
    }

    private fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase()
        require(normalized.length in 3..254 && EMAIL.matches(normalized)) { "invalid_email" }
        return normalized
    }

    private companion object {
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
