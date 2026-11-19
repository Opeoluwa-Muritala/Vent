package app.vent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionClockTest {
    @Test
    fun noonAndMidnightMapToDifferentHours() {
        assertEquals(0, sessionClockMinutes("12:00 AM"))
        assertEquals(720, sessionClockMinutes("12:00 PM"))
    }

    @Test
    fun sessionRequiresTwoTimesInSameDayOrder() {
        assertTrue(validSessionTimeRange("10:00 AM – 11:15 AM"))
        assertTrue(validSessionTimeRange("11:30 AM - 12:30 PM"))
        assertFalse(validSessionTimeRange("11:15 AM – 10:00 AM"))
        assertFalse(validSessionTimeRange("10:00 AM – "))
    }
}
