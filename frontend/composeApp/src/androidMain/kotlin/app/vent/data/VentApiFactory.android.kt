package app.vent.data

actual fun createVentApi(): VentApi =
    VentApiClient(
        CONFIGURED_MOBILE_BACKEND_URL.ifBlank { "http://10.0.2.2:8080" },
    )
