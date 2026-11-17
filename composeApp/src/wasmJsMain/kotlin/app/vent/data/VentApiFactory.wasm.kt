package app.vent.data

import kotlinx.browser.window

actual fun createVentApi(): VentApi = VentApiClient("${window.location.protocol}//${window.location.hostname}:8081")
