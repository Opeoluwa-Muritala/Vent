package app.vent.server

import app.vent.api.ApiError
import app.vent.api.HealthResponse
import app.vent.api.AuthResponse
import app.vent.api.LoginRequest
import app.vent.api.RefreshRequest
import app.vent.api.RegisterRequest
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
    val authService = AuthService(AuthRepository(dataSource), PasswordHasher(), tokenService)
    val checkInTokens = CheckInTokenService(dataSource)
    install(CallLogging)
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
        exception<EmailAlreadyExists> { call, _ -> call.respond(HttpStatusCode.Conflict, ApiError("email_exists", "An account already uses that email")) }
        exception<Forbidden> { call, _ -> call.respond(HttpStatusCode.Forbidden, ApiError("forbidden", "You do not have permission for this action")) }
        exception<IllegalArgumentException> { call, error -> call.respond(HttpStatusCode.BadRequest, ApiError(error.message ?: "invalid_request", "The request is invalid")) }
        exception<Throwable> { call, _ -> call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error", "The request could not be completed")) }
    }
    install(CORS) {
        allowMethod(HttpMethod.Get); allowMethod(HttpMethod.Post); allowMethod(HttpMethod.Put); allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization); allowHeader(HttpHeaders.ContentType)
        config.allowedOrigins.forEach { origin -> allowHost(origin.removePrefix("https://").removePrefix("http://"), schemes = listOf("https", "http")) }
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
                post("/auth/register") {
                    val request = call.receive<RegisterRequest>()
                    val pair = authService.register(request.name, request.email, request.password.toCharArray())
                    call.respond(HttpStatusCode.Created, AuthResponse(pair.accessToken, pair.refreshToken, pair.expiresInSeconds))
                }
                post("/auth/login") {
                    val request = call.receive<LoginRequest>()
                    val pair = authService.login(request.email, request.password.toCharArray())
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("invalid_credentials", "Email or password is incorrect"))
                    call.respond(AuthResponse(pair.accessToken, pair.refreshToken, pair.expiresInSeconds))
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
                    call.respond(mapOf("id" to userId))
                }
                ventDemoRoutes(AccessControl(dataSource), checkInTokens)
            }
        }
    }
}