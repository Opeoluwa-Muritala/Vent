package app.vent.data

actual fun createVentApi(): VentApi = VentApiClient("http://127.0.0.1:8080")
