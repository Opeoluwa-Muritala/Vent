package app.vent

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

@Composable
internal expect fun BindBrowserNavigation(backStack: NavBackStack<NavKey>)
