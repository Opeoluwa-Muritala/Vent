package app.vent

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.github.terrakok.navigation3.browser.ChronologicalBrowserNavigation
import com.github.terrakok.navigation3.browser.buildBrowserHistoryFragment
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentName

@Composable
internal actual fun BindBrowserNavigation(backStack: NavBackStack<NavKey>) {
    ChronologicalBrowserNavigation(
        backStack = backStack,
        saveKey = { route -> buildBrowserHistoryFragment(route.toScreen().name.lowercase()) },
        restoreKey = { fragment ->
            when (getBrowserHistoryFragmentName(fragment)) {
                "event" -> EventRoute
                "schedule" -> ScheduleRoute
                "seat" -> SeatRoute
                "quests" -> QuestsRoute
                "profile" -> ProfileRoute
                "organizer" -> OrganizerRoute
                else -> null
            }
        },
    )
}
