package app.vent.server

class AuthService(private val repository: AuthRepository, private val hasher: PasswordHasher, private val tokens: TokenService) {
    fun register(name: String, email: String, password: CharArray): TokenPair {
        validate(name, email, password)
        val user = repository.createUser(name, email, hasher.hash(password))
        return issue(user.id)
    }
    fun login(email: String, password: CharArray): TokenPair? {
        if (email.length !in 3..254 || password.size !in 12..128) { password.fill('\u0000'); return null }
        val user = repository.findByEmail(email)
        val valid = user != null && hasher.verify(user.passwordHash, password)
        if (!valid) { password.fill('\u0000'); return null }
        return issue(requireNotNull(user).id)
    }
    fun refresh(refreshToken: String): TokenPair? {
        if (refreshToken.length !in 43..128) return null
        val replacement = tokens.refreshToken()
        val userId = repository.rotateRefreshToken(tokens.hashRefreshToken(refreshToken), tokens.hashRefreshToken(replacement)) ?: return null
        val access = tokens.accessToken(userId)
        return TokenPair(access.first, replacement, access.second)
    }
    private fun issue(userId: String): TokenPair {
        val access = tokens.accessToken(userId)
        val refresh = tokens.refreshToken()
        repository.storeRefreshToken(userId, tokens.hashRefreshToken(refresh))
        return TokenPair(access.first, refresh, access.second)
    }
    private fun validate(name: String, email: String, password: CharArray) {
        require(name.trim().length in 1..120) { "invalid_name" }
        require(email.length in 3..254 && EMAIL.matches(email.trim())) { "invalid_email" }
        require(password.size in 12..128) { "invalid_password" }
    }
    private companion object { val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") }
}