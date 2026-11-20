package app.vent.data

import app.vent.api.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface VentApi {
    suspend fun attendeePass(eventId: String): AttendeePassDto = error("Pass issuance is unavailable")
    suspend fun loadPreferences(): UserPreferencesDto = error("Preference sync is unavailable")
    suspend fun savePreferences(preferences: UserPreferencesDto): Unit = error("Preference sync is unavailable")
    suspend fun isOrganizer(): Boolean = false
    suspend fun requestOtp(email: String): OtpRequestResponse
    suspend fun verifyOtp(email: String, code: String): AuthResponse
    suspend fun events(): List<EventDto>
    suspend fun hall(id: String): HallDto
    suspend fun seats(sessionId: String, since: Long? = null): SeatSnapshotDto
    suspend fun checkIn(sessionId: String, request: CheckInRequest): CheckInResponse
    suspend fun quests(eventId: String): List<QuestDto>
    suspend fun wrapped(eventId: String): WrappedCardDto
    fun clearSession()
}

class VentApiClient(private val baseUrl: String, private val client: HttpClient = HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = false; explicitNulls = false }) } }) : VentApi {
    override suspend fun attendeePass(eventId: String): AttendeePassDto =
        authorizedGet("/api/v1/events/${eventId.also { require(it.matches(Regex("[0-9a-fA-F-]{36}"))) }}/pass")
    override suspend fun loadPreferences(): UserPreferencesDto = authorizedGet("/api/v1/me/preferences")

    override suspend fun savePreferences(preferences: UserPreferencesDto) {
        val response = withSession { accessToken ->
            client.put("$baseUrl/api/v1/me/preferences") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(preferences)
            }
        }
        check(response.status.isSuccess()) { "Preference upload failed" }
    }
    override suspend fun isOrganizer(): Boolean =
        authorizedGet<Map<String, String>>("/api/v1/me")["role"] == "ORGANIZER"
    private var session: AuthResponse? = null
    private var sessionGeneration = 0
    private val refreshLock = Mutex()
    override suspend fun requestOtp(email: String): OtpRequestResponse = client.post("$baseUrl/api/v1/auth/otp/request") {
        contentType(ContentType.Application.Json)
        setBody(OtpRequest(email))
    }.body()
    override suspend fun verifyOtp(email: String, code: String): AuthResponse = client.post("$baseUrl/api/v1/auth/otp/verify") {
        contentType(ContentType.Application.Json)
        setBody(OtpVerifyRequest(email, code))
    }.body<AuthResponse>().also {
        sessionGeneration++
        session = it
    }
    override suspend fun events(): List<EventDto> = authorizedGet("/api/v1/events")
    override suspend fun hall(id: String): HallDto = authorizedGet("/api/v1/halls/$id")
    override suspend fun seats(sessionId: String, since: Long?): SeatSnapshotDto = authorizedGet("/api/v1/sessions/$sessionId/seats"+(since?.let{"?since=$it"}?:""))
    override suspend fun checkIn(sessionId: String, request: CheckInRequest): CheckInResponse = client.post("$baseUrl/api/v1/sessions/$sessionId/checkin") { token()?.let(::bearerAuth); contentType(ContentType.Application.Json); setBody(request) }.body()
    override suspend fun quests(eventId: String): List<QuestDto> = authorizedGet("/api/v1/events/$eventId/quests")
    override suspend fun wrapped(eventId: String): WrappedCardDto = authorizedGet("/api/v1/events/$eventId/wrapped/me")
    private suspend inline fun <reified T> authorizedGet(path: String): T {
        val response = withSession { accessToken ->
            client.get("$baseUrl$path") { bearerAuth(accessToken) }
        }
        check(response.status.isSuccess()) { "Request failed" }
        return response.body()
    }

    private suspend fun withSession(request: suspend (String) -> HttpResponse): HttpResponse {
        val previous = checkNotNull(session) { "Sign in first" }
        val generation = sessionGeneration
        val response = request(previous.accessToken)
        if (response.status != HttpStatusCode.Unauthorized) return response
        val renewed = refreshLock.withLock {
            if (sessionGeneration != generation) return@withLock null
            if (session !== previous) return@withLock session
            val refresh = client.post("$baseUrl/api/v1/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(previous.refreshToken))
            }
            if (!refresh.status.isSuccess()) return@withLock null
            val replacement = refresh.body<AuthResponse>()
            if (session !== previous) return@withLock null
            session = replacement
            replacement
        }
        return if (renewed == null || sessionGeneration != generation) response else request(renewed.accessToken)
    }

    private fun token(): String? = session?.accessToken
    override fun clearSession() {
        sessionGeneration++
        session = null
    }
}
