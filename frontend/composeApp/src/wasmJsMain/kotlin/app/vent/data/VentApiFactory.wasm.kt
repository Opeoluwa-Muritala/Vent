package app.vent.data

import kotlinx.browser.window

actual fun createVentApi(): VentApi = VentApiClient(
    CONFIGURED_BACKEND_URL.ifBlank { "${window.location.protocol}//${window.location.hostname}:8081" },
)
