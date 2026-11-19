package app.vent.server

import app.vent.api.ApiError
import app.vent.api.HealthResponse
import app.vent.api.AuthResponse
import app.vent.api.OtpRequest
import app.vent.api.OtpRequestResponse
import app.vent.api.OtpVerifyRequest
import app.vent.api.RefreshRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

fun main() {
    val config = AppConfig.fromEnvironment()
    embeddedServer(Netty, host = "0.0.0.0", port = config.port) { module(config) }.start(wait = true)
}

fun Application.module(config: AppConfig = AppConfig.fromEnvironment()) {
    val dataSource = DatabaseFactory.connect(config)
    val tokenService = TokenService(config.jwtSecret)
    val authService = AuthService(AuthRepository(dataSource), tokenService, DemoEmailSender(config.demoOtpLogging))
    val checkInTokens = CheckInTokenService(dataSource)
    install(CallLogging)
    install(XForwardedHeaders)
    install(DefaultHeaders) {
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("Cache-Control", "no-store")
    }
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = false; explicitNulls = false; encodeDefaults = true }) }
    install(StatusPages) {
        exception<OtpRateLimited> { call, _ -> call.respond(HttpStatusCode.TooManyRequests, ApiError("rate_limited", "Too many tries. Wait a minute and try again.")) }
        exception<Forbidden> { call, _ -> call.respond(HttpStatusCode.Forbidden, ApiError("forbidden", "You do not have permission for this action")) }
        exception<IllegalArgumentException> { call, error -> call.respond(HttpStatusCode.BadRequest, ApiError(error.message ?: "invalid_request", "The request is invalid")) }
        exception<Throwable> { call, _ -> call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error", "The request could not be completed")) }
    }
    install(CORS) {
        allowMethod(HttpMethod.Get); allowMethod(HttpMethod.Post); allowMethod(HttpMethod.Put); allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization); allowHeader(HttpHeaders.ContentType)
        config.allowedOrigins.forEach { origin ->
            val uri = java.net.URI(origin)
            require(
                uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null &&
                    uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null,
            ) { "ALLOWED_ORIGINS must contain HTTP or HTTPS origins without paths" }
            allowHost(uri.rawAuthority, schemes = listOf(uri.scheme))
        }
    }
    install(RateLimit) {
        register(RateLimitName("auth")) { rateLimiter(limit = 10, refillPeriod = 1.minutes) }
        register(RateLimitName("write")) { rateLimiter(limit = 60, refillPeriod = 1.minutes) }
    }
    install(io.ktor.server.auth.Authentication) {
        jwt("auth-jwt") {
            realm = "vent"
            verifier(tokenService.verifier())
            validate { credential -> credential.payload.subject?.takeIf { it.isNotBlank() }?.let(::UserPrincipal) }
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, ApiError("unauthorized", "Authentication is required")) }
        }
    }
    routing {
        route("/api/v1") {
            get("/health") { call.respond(HealthResponse("ok")) }
            rateLimit(RateLimitName("auth")) {
                post("/auth/otp/request") {
                    val request = call.receive<OtpRequest>()
                    authService.requestCode(request.email, call.request.origin.remoteHost)
                    call.respond(HttpStatusCode.Accepted, OtpRequestResponse())
                }
                post("/auth/otp/verify") {
                    val request = call.receive<OtpVerifyRequest>()
                    when (val result = authService.verifyCode(request.email, request.code)) {
                        is OtpVerification.Success -> call.respond(AuthResponse(result.tokens.accessToken, result.tokens.refreshToken, result.tokens.expiresInSeconds))
                        is OtpVerification.Wrong -> call.respond(HttpStatusCode.Unauthorized, ApiError("wrong_code", "That code isn't right. ${result.triesLeft} tries left."))
                        OtpVerification.Expired -> call.respond(HttpStatusCode.Unauthorized, ApiError("expired_code", "That code has expired. Send a new one."))
                        OtpVerification.Locked -> call.respond(HttpStatusCode.TooManyRequests, ApiError("code_locked", "Too many wrong tries. Send a new code."))
                    }
                }
                post("/auth/refresh") {
                    val request = call.receive<RefreshRequest>()
                    val pair = authService.refresh(request.refreshToken)
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("invalid_refresh_token", "Sign in again"))
                    call.respond(AuthResponse(pair.accessToken, pair.refreshToken, pair.expiresInSeconds))
                }
            }
            authenticate("auth-jwt") {
                get("/me") {
                    val userId = requireNotNull(call.principal<UserPrincipal>()).userId
                    val organizer = dataSource.connection.use { connection ->
                        connection.prepareStatement(
                            "SELECT EXISTS (SELECT 1 FROM user_roles WHERE user_id = ?::uuid AND role = 'ORGANIZER')",
                        ).use { statement ->
                            statement.setString(1, userId)
                            statement.executeQuery().use { rows -> rows.next() && rows.getBoolean(1) }
                        }
                    }
                    call.respond(mapOf("id" to userId, "role" to if (organizer) "ORGANIZER" else "ATTENDEE"))
                }
                ventDemoRoutes(AccessControl(dataSource), checkInTokens, ScheduleRepository(dataSource))
            }
        }
    }
}
