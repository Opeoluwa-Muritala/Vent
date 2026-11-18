package app.vent.data

import app.vent.api.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

interface VentApi {
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
    override suspend fun isOrganizer(): Boolean =
        authorizedGet<Map<String, String>>("/api/v1/me")["role"] == "ORGANIZER"
    private var accessToken: String? = null
    override suspend fun requestOtp(email: String): OtpRequestResponse = client.post("$baseUrl/api/v1/auth/otp/request") {
        contentType(ContentType.Application.Json)
        setBody(OtpRequest(email))
    }.body()
    override suspend fun verifyOtp(email: String, code: String): AuthResponse = client.post("$baseUrl/api/v1/auth/otp/verify") {
        contentType(ContentType.Application.Json)
        setBody(OtpVerifyRequest(email, code))
    }.body<AuthResponse>().also { accessToken = it.accessToken }
    override suspend fun events(): List<EventDto> = authorizedGet("/api/v1/events")
    override suspend fun hall(id: String): HallDto = authorizedGet("/api/v1/halls/$id")
    override suspend fun seats(sessionId: String, since: Long?): SeatSnapshotDto = authorizedGet("/api/v1/sessions/$sessionId/seats"+(since?.let{"?since=$it"}?:""))
    override suspend fun checkIn(sessionId: String, request: CheckInRequest): CheckInResponse = client.post("$baseUrl/api/v1/sessions/$sessionId/checkin") { token()?.let(::bearerAuth); contentType(ContentType.Application.Json); setBody(request) }.body()
    override suspend fun quests(eventId: String): List<QuestDto> = authorizedGet("/api/v1/events/$eventId/quests")
    override suspend fun wrapped(eventId: String): WrappedCardDto = authorizedGet("/api/v1/events/$eventId/wrapped/me")
    private suspend inline fun <reified T> authorizedGet(path: String): T = client.get("$baseUrl$path") { token()?.let(::bearerAuth) }.body()
    private fun token(): String? = accessToken
    override fun clearSession() { accessToken=null }
}
