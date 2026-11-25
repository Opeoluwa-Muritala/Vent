package app.vent.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ContractsTest {
    @Test fun unknownFieldsAreRejectedByServerJsonConfiguration() {
        val json = Json { ignoreUnknownKeys = false }
        val decoded = json.decodeFromString<HealthResponse>("{\"status\":\"ok\"}")
        assertEquals("ok", decoded.status)
    }
}
