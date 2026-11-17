package app.vent.server

import java.net.URI

data class AppConfig(
    val port: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val jwtSecret: String,
    val demoOtpLogging: Boolean,
    val allowedOrigins: Set<String>,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig {
            val secret = env["JWT_SECRET"] ?: error("JWT_SECRET is required")
            require(secret.toByteArray().size >= 32) { "JWT_SECRET must be at least 32 bytes" }
            val database = databaseSettings(env)
            return AppConfig(
                port = env["PORT"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 8080,
                databaseUrl = database.first,
                databaseUser = database.second,
                databasePassword = database.third,
                jwtSecret = secret,
                demoOtpLogging = env["VENT_DEMO_OTP_LOGGING"].equals("true", ignoreCase = true),
                allowedOrigins = env["ALLOWED_ORIGINS"].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet(),
            )
        }

        private fun databaseSettings(env: Map<String, String>): Triple<String, String, String> {
            val raw = env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/vent"
            if (raw.startsWith("jdbc:")) return Triple(raw, env["DATABASE_USER"] ?: "vent", env["DATABASE_PASSWORD"] ?: "")
            val uri = URI(raw)
            require(uri.scheme == "postgres" || uri.scheme == "postgresql") { "DATABASE_URL must use PostgreSQL" }
            val credentials = uri.userInfo.orEmpty().split(':', limit = 2)
            val jdbc = "jdbc:postgresql://${uri.host}:${if (uri.port > 0) uri.port else 5432}${uri.path}" + (uri.query?.let { "?$it" } ?: "")
            return Triple(jdbc, credentials.getOrElse(0) { "" }, credentials.getOrElse(1) { "" })
        }
    }
}
