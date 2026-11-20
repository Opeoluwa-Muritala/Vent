package app.vent.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventPaletteTest {
    @Test fun defaultPaletteMatchesEventsIdentity() {
        assertEquals(0xFFB9A7FF.toInt(), EventPalette.accents(null, true).primary)
        assertEquals(0xFF5B3FD6.toInt(), EventPalette.accents(null, false).primary)
    }

    @Test fun everySeedHasReadableActionAndSelectionText() {
        (EventPalette.seeds.values + listOf(0xFF000000.toInt(), -1)).forEach { seed ->
            listOf(false, true).forEach { dark ->
                val palette = EventPalette.accents(seed, dark)
                assertTrue(EventPalette.contrast(palette.primary, palette.onPrimary) >= 4.5)
                assertTrue(EventPalette.contrast(palette.secondary, palette.onSecondary) >= 4.5)
            }
        }
    }
}
